(ns twitter.core
  "Main namespace starting an infinite loop waiting for twitter queries
  and searching for matching tweets in pre-defined intervals."
  (:require
   [clojure.core.async :as async]
   [clojure.edn :as edn]
   [twitter.api :as api]
   [twitter.processor :as processor]
   [twitter.server :as server]))

;; TODO: externalize configuration completely
(def tweets-fetch-interval 15000)
(def twitter-creds (edn/read-string (slurp ".creds.edn")))

(defn- authenticate []
  (api/authenticate twitter-creds))

(defn twitter-loop [query-channel]
  ;; start with the default search query:
  (async/>!! query-channel "#clojure")

  (loop [old-query nil
         auth-state (authenticate)
         seen #{}] ;; `seen` is set of tweet ids
    (let [[new-query port] (async/alts!! [query-channel
                                          (async/timeout tweets-fetch-interval)])
          query (or new-query old-query)
          _ (when new-query (println " Got new query: " new-query))
          [updated-auth-state updated-seen]
          ;; if timeout happens, keep using the old query
          ;; otherwise search using the new query got from search-channel
          (processor/process-tweets query
                                    auth-state
                                    ;; tweet cache is emptied if we get a new search query via channel
                                    (if (= port query-channel) #{} seen))]
      (recur query updated-auth-state updated-seen))))

(defn -main
  "Runs the main loop polling for tweets."
  [& args]
  ;; TODO: exception handler?
  (let [query-channel (async/chan 10)]
    (server/start-server query-channel)
    (twitter-loop query-channel)))

(comment
  (def my-auth-state (api/authenticate twitter-creds))

  (search my-auth-state)

  (def main-future (future (try (-main) (catch Exception e (println "ERROR: " e)))))

  (future-cancel main-future)


  )
