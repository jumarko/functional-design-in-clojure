(ns twitter.poster.app
  "This is the main Twitter Poster entry point which allows you to schedule a tweet.
  This can expose a rest API or a UI route triggered via HTTP.
  It can also be called from other namespaces for quick REPL experiments.

  Stateful HTTP server running on port 8082 and listening for tweets to be scheduled
  via the `/tweets` endpoint (POST).

  You can test with CURL
     curl -H 'Content-Type: application/json' -X POST -v localhost:8082/tweets -d '{\"text\" : \"My First Tweet\", \"post-at\": \"2019-09-06T11:40:00+02:00\"}'"
  (:require [clojure.core.async :as a]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [com.walmartlabs.system-viz :refer [visualize-system]]
            [twitter.poster.db :as db]
            [twitter.poster.scheduler :as scheduler]
            [twitter.poster.twitter-api :as twitter-api]
            [twitter.poster.worker :as worker]
            [twitter.poster.server :as server]
            ;; make sure all specs are loaded early in the process
            [twitter.poster.spec :as spec]))

;;;; Problem: I wanna craft a tweet and schedule it to be posted later.
;;;;

(defn new-system
  [{:keys [scheduler-interval-ms server-port] :as config}]
  (component/system-map
   :tweets-channel (a/chan 10) ; tweets scheduled by a user posted on this channel
   :scheduler-channel (a/chan 10) ; scheduler posts 'current time' on this channel every scheduling interval
   :tweets-to-post-channel (a/chan 10) ; tweets to be posted are put here by the worker and picked up by twitter-api
   :posted-tweets-channel (a/chan 10) ; finally, posted tweets are put here and then processed by worker to update DB data
   :database (db/make-database)
   :scheduler (component/using
               (scheduler/make-scheduler scheduler-interval-ms)
               [:scheduler-channel])
   ;; TODO: why this doesn't fail during component/start when I don't pass the required dependencies??
   :server (component/using
            (server/make-server server-port)
            [:tweets-channel :database])
   :twitter-api-poster (twitter-api/make-twitter-api-poster config)
   :twitter-api (component/using (twitter-api/make-twitter-api)
                                 [:tweets-to-post-channel :posted-tweets-channel
                                  :twitter-api-poster])
   :worker (component/using
            (worker/make-worker)
            [:database :tweets-channel :scheduler-channel :tweets-to-post-channel :posted-tweets-channel])))

(defn- install-uncaught-exception-handler!
  "Uncaught exception handler is usefula when a thread suddenly dies such as thread
  spawned inside a go block.
  Note that this doesn't apply to `future` (use `logging-future+` macro for that)"
  []
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (log/error ex "Uncaught exception on" (.getName thread))))))

(defn start-app
  ([]
   (start-app 
    (new-system
     ;; TODO config
     (merge
      {:scheduler-interval-ms 10000
       :server-port 8082}
      (edn/read-string (slurp ".creds.edn"))))))
  ([system]
   (install-uncaught-exception-handler!)
   (component/start system)))

(defn stop-app [{:keys [tweets-channel scheduler-channel tweets-to-post-channel posted-tweets-channel]
                 :as app}]
  (when app 
    (a/close! tweets-channel)
    (a/close! scheduler-channel)
    (a/close! tweets-to-post-channel)
    (a/close! posted-tweets-channel)
    (component/stop app)))

;; Check https://github.com/weavejester/compojure/wiki/Destructuring-Syntax
;; and https://github.com/ring-clojure/ring/wiki/Parameters

(defn restart [app]
  (stop-app app)
  (start-app))

(comment

  ;; TODO: check NoClassDefFoundError during startup:
    ;; WARNING: An exception was thrown by aleph.netty$wrap_future$reify__15436.operationComplete()
    ;; java.lang.NoClassDefFoundError: Could not initialize class manifold.deferred.Deferred$fn__10876

  (def my-app (start-app))

  (stop-app my-app)

  (def my-app (restart my-app))

  ;; visualize the system (generates new temp pdf file)
  (visualize-system my-app)

  ;; end comment
  )
