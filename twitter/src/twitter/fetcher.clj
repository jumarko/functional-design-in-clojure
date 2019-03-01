(ns twitter.fetcher
  "Component that pools for available tweets based on the query retrieved
  from core.async channel and prints them out to standard output.
  Uses twitter api to fetch tweets."
  (:require
   [clojure.edn :as edn]
   [clojure.core.async :as async]
   [com.stuartsierra.component :as component]
   [twitter.api :as api]
   [twitter.processor :as processor]))

;; TODO: externalize configuration completely
(def tweets-fetch-interval 15000)
(def twitter-creds (edn/read-string (slurp ".creds.edn")))

(defn- authenticate []
  (api/authenticate twitter-creds))

(defn twitter-loop [query-channel]
  ;; start with the default search query:
  (async/>!! query-channel "#clojure")
  ;; keep processing until we get another query or timeout happens
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

(defrecord Fetcher [loop-future query-channel]
  component/Lifecycle
  (start [this]
    (let [loop-future (future (twitter-loop query-channel))]
      (assoc this
             :query-channel query-channel
             :loop-future loop-future)))
  (stop [this]
    (future-cancel loop-future)))

(defn new-component []
  ;; query-channel injected by the system (see core.clj)
  (map->Fetcher {}))
