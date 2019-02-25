(ns twitter.core
  (:require
   [clojure.edn :as edn]
   [twitter.api :as api]
   [twitter.processor :as processor]
   [twitter.server :as server]))

;; TODO: externalize configuration completely
(def sleep-time 15000)
(def twitter-creds (edn/read-string (slurp ".creds.edn")))

(defn- authenticate []
  (api/authenticate twitter-creds))

(defn twitter-loop []
  (loop [auth-state (authenticate)
         seen #{}] ;; `seen` is set of tweet ids
    (let [[updated-auth-state updated-seen] (processor/process-tweets auth-state seen sleep-time)]
      (recur updated-auth-state updated-seen))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (server/start-server)
  (twitter-loop))

(comment
  (def my-auth-state (api/authenticate twitter-creds))

  (search my-auth-state)

  (def main-future (future (-main)))

  (future-cancel main-future)


  )
