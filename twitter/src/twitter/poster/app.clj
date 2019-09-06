(ns twitter.poster.app
  "This is the main Twitter Poster entry point which allows you to schedule a tweet.
  This can expose a rest API or a UI route triggered via HTTP.
  It can also be called from other namespaces for quick REPL experiments.

  Stateful HTTP server running on port 8082 and listening for tweets to be scheduled
  via the `/tweets` endpoint (POST).

  You can test the server simply via CURL
      curl localhost:8082/tweets -d 'TODO: !!!'"
  (:require
   [aleph.http :as http]
   [clojure.core.async :as async]
   [com.stuartsierra.component :as component]
   [compojure.core :refer [defroutes GET POST routes]]
   [ring.middleware.params :as ring-params]
   [twitter.poster.db :as db]
   [twitter.poster.twitter-api :as twitter-api]
   [twitter.poster.scheduler :as scheduler]
   [twitter.poster.worker :as worker])
  (:require [clojure.spec.alpha :as s]))


;;;; Problem: I wanna craft a tweet and schedule it to be posted later.
;;;;

;;; Just specify the content:

;; here I intentionally left out specs for simple attributes which are obvious
;; to experiment if they would bring some benefits or not
;; (s/def :tweet/text string?)
;; (s/def :tweet/db-id long?)
;; (s/def :tweet/tweet-id string?)
;; (s/def :tweet/posted? boolean?)

;; TODO: what's a proper date/time representation?
;; ZonedDateTime should be fine because what we really want to do is to schedule a tweet to be posted
;; in user's timezone
(s/def :tweet/post-at (partial instance? java.time.ZonedDateTime))

;; TODO: attributes like post-at are really specific to scheduling and not shared in all use cases
;; => maybe it should be separated and provided as a different param of `schedule-tweet` fn?
(s/def :tweet/tweet (s/keys :req [:tweet/text :tweet/post-at]
                                    ;; IDs and posted? are only filled once the tweet is posted
                                    ;; TODO: the disinction between db-id and tweet-it looks awkward
                                    :opt [:tweet/db-id
                                          :tweet/tweet-id
                                          :tweet/posted?]))
;; TODO make this work
;; You can test with CURL
;;   curl -X POST -v localhost:8082/tweets -d '{"text" : "My First Tweet", "post-at": "2019-09-06T11:40:00+02:00}'
(defn schedule-tweet [tweets-channel {:keys [text post-at] :as _req}]
  ;; TODO validate tweet
  ;; where to do coercions such as for `post-at`??
  (let [transformed-tweet {:tweet/text text
                           :tweet/post-at (java.time.ZonedDateTime/parse post-at)}]
    (if-not (s/valid? :tweet/tweet transformed-tweet)
      {:status 400
       :body (str "Bad data: " (s/explain-str :tweet/tweet transformed-tweet))}

      ;; backpressure? - blocking unless there's some space in the buffer??
      ;; TODO: which version of 'put' use?
      (do (async/>!! tweets-channel transformed-tweet)
          {:status 201
           :body {:status "scheduled"}
           :headers {"Contet-Type" "application/json"}}))))

(defn- make-app-routes [tweets-channel]
  (routes (POST "/tweets" req (schedule-tweet tweets-channel req))))

(defn- make-app [tweets-channel]
  (-> (make-app-routes tweets-channel)
      ring-params/wrap-params))

(defn stop-server [http-server]
  (when http-server
    (println "Stopping the server...")
    (.close http-server)))

(defn start-server [port tweets-channel]
  (println "Starting the server...")
  (let [server (http/start-server (make-app tweets-channel)
                                  {:port port})]
    (println "Server started.")
    server))

;; TODO: it seems that Server component is frequent enough
;; that it could be part of reusable library of components
;; -> check https://github.com/danielsz/system
(defrecord Server [port http-server tweets-channel]
  component/Lifecycle
  (start [this]
    (assoc this :http-server (start-server
                              port
                              tweets-channel)))
  (stop [this]
    (stop-server http-server)))

(defn make-server [port]
  ;; tweets-channel injected by the system (see core.clj)
  (map->Server {:port port}))

(defn new-system
  [{:keys [scheduler-interval-ms server-port] :as _config}]
  (component/system-map
   :tweets-channel (async/chan 10) ; tweets scheduled by a user posted on this channel
   :scheduler-channel (async/chan 10) ; scheduler posts 'current time' on this channel every scheduling interval
   :scheduler (component/using
               (scheduler/make-scheduler scheduler-interval-ms) [:scheduler-channel])
   ;; TODO: why this doesn't fail during component/start when I don't pass the required dependencies??
   :server (component/using (make-server server-port) [:tweets-channel])
   :twitter-api (twitter-api/make-twitter-api)
   :worker (component/using
            (worker/make-worker) [:tweets-channel :scheduler-channel])))

(defn start-app []
  ;; TODO config
  (component/start
   (new-system
    {:scheduler-interval-ms 1000
     :server-port 8082})))

(defn stop-app [app]
  (component/stop app))

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
  (restart my-app)


  ;; end comment
  )
