(ns twitter.server
  "Stateful HTTP server running on port 8081 and listening for search queries on `/search` endpoint (POST)
  and updating the main thread to take the new query into account.
  Can be started stopped via given methods.
  The HTTP server instance is tracked internally and not exposed to the client."
  (:require [aleph.http :as http]
            [compojure.core :refer [defroutes GET POST]]))

(defroutes app
  (GET "/" [] "Search string updated to: <b>TODO</b>"))

(defonce ^:private server (atom nil))

(defn stop-server []
  (when @server
    (println "Stopping the server...")
    (.close @server)))

(defn start-server []
  (stop-server)
  (println "Starting the server...")
  (reset! server
          (http/start-server app
                             ;; TODO: port should be configurable
                             {:port 8081}))
  (println "Server started.")
  )



(comment

  (start-server)

  (stop-server)

  )
