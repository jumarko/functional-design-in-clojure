(ns twitter.server
  "Stateful HTTP server running on port 8081 and listening for search queries on `/search` endpoint (POST)
  and updating the main thread to take the new query into account.
  Can be started stopped via given methods.
  The HTTP server instance is tracked internally and not exposed to the client."
  (:require [aleph.http :as http]
            [clojure.core.async :as async]
            [compojure.core :refer [defroutes GET POST routes]]
            [ring.middleware.params :as rparams]))

(defn handle-search-query-update [query-channel query]
  (async/>!! query-channel query)
  (format "Search string updated to: <b>%s</b>" query))

;; Check https://github.com/weavejester/compojure/wiki/Destructuring-Syntax
;; and https://github.com/ring-clojure/ring/wiki/Parameters

(defn- make-app-routes [query-channel]
  (routes (POST "/" [q] (handle-search-query-update query-channel q))))

(defn- make-app [query-channel]
  (-> (make-app-routes query-channel)
      rparams/wrap-params))

(defonce ^:private server (atom nil))

(defn stop-server []
  (when @server
    (println "Stopping the server...")
    (.close @server)))

(defn start-server [query-channel]
  (stop-server)
  (println "Starting the server...")
  (reset! server
          (http/start-server (make-app query-channel)
                             ;; TODO: port should be configurable
                             {:port 8081}))
  (println "Server started."))

(comment

  (start-server)

  (stop-server)

  ;; end comment
  )
