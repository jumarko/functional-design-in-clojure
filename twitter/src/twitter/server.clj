(ns twitter.server
  "Stateful HTTP server running on port 8081 and listening for search queries on `/search` endpoint (POST)
  and updating the main thread to take the new query into account.
  Can be started stopped via given methods.
  The HTTP server instance is tracked internally and not exposed to the client.

  You can test the server simply via CURL
      curl localhost:8081/ -d 'q=#clojurescript'"
  (:require
   [aleph.http :as http]
   [clojure.core.async :as async]
   [com.stuartsierra.component :as component]
   [compojure.core :refer [defroutes GET POST routes]]
   [ring.middleware.params :as ring-params]))

(defn handle-search-query-update [query-channel query]
  (async/>!! query-channel query)
  (format "Search string updated to: <b>%s</b>" query))

;; Check https://github.com/weavejester/compojure/wiki/Destructuring-Syntax
;; and https://github.com/ring-clojure/ring/wiki/Parameters

(defn- make-app-routes [query-channel]
  (routes (POST "/" [q] (handle-search-query-update query-channel q))))

(defn- make-app [query-channel]
  (-> (make-app-routes query-channel)
      ring-params/wrap-params))

(defn stop-server [http-server]
  (when http-server
    (println "Stopping the server...")
    (.close http-server)))

(defn start-server [port query-channel]
  (println "Starting the server...")
  (let [server (http/start-server (make-app query-channel)
                                  {:port port})]
    (println "Server started.")
    server))

(defrecord Server [http-server query-channel]
  component/Lifecycle
  (start [this]
    (assoc this :http-server (start-server
                         ;; TODO: port should be configurable
                         8081
                         query-channel)))
  (stop [this]
    (stop-server http-server)))

(defn new-component []
  ;; query-channel injected by the system (see core.clj)
  (map->Server {}))

(comment

  (start-server)

  (stop-server)

  ;; end comment
  )
