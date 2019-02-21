(ns twitter.server
  "HTTP server running on port 8081 and listening for search queries on `/search` endpoint (POST)
  and updating the main thread to take the new query into account."
  (:require [aleph.http :as http]
            [compojure.core :refer [defroutes GET POST]]))

(defroutes app
  (GET "/" [] "Search string updated to: <b>TODO</b>"))

(defonce ^:private server (atom nil))

(defn stop-server [server]
  (when server
    (println "Stopping the server...")
    (.close server)))

(defn start-server []
  (stop-server @server)
  (println "Starting the server...")
  (reset! server
          (http/start-server app
                             {:port 8081}))
  (println "Server started."))



(comment

  (def my-server (start-server))



  )
