(ns twitter.poster.server
  (:require [aleph.http :as http]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [ring.middleware.json :as ring-json]
            [ring.middleware.params :as ring-params]
            [twitter.poster.routes :as routes]))

(defn- make-app [tweets-channel]
  (-> (routes/make-routes tweets-channel)
      ring-params/wrap-params
      ;; could use `ring-json/wrap-json-params` too: https://github.com/ring-clojure/ring-json
      (ring-json/wrap-json-body {:keywords? true})
      ring-json/wrap-json-response))

(defn stop-server [http-server]
  (when http-server
    (log/info "Stopping the server...")
    (.close http-server)))

(defn start-server [port tweets-channel]
  (log/info "Starting the server...")
  (let [server (http/start-server (make-app tweets-channel)
                                  {:port port})]
    (log/info "Server started.")
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

