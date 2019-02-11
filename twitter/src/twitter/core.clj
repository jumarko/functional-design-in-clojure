(ns twitter.core
  (:require
   [clojure.edn :as edn]
   [clojure.repl :refer [pst]]
   [twitter.api :as api]))

(def twitter-creds (edn/read-string (slurp ".creds.edn")))
(def sleep-time 15000)

(defn- authenticate []
  (api/authenticate twitter-creds))

(defn- search [auth-handle]
  (try 
    (api/search auth-handle "#clojure")
    (catch Exception e
      ;; TODO: it might be too late to catch data here
      ;; since we would need reponse boy if non-ok HTTP status is thrown
      ;; => if we don't do that in `twitter.api` ns then http client details will leak
      (pst e))))

(defn format-tweets [tweets]
  (let [format-tweet (fn format-tweet [tweet]
                       (format " * %s tweeted: '%s'"
                               (:user/name tweet)
                               (some-> tweet :tweet/text (clojure.string/replace "\n" " "))))]
    (if (empty? tweets)
      ["NO NEW TWEETS!"]
      (mapv format-tweet tweets))))

;; TODO: use proper cache evicting old entries to prevent out of memory
;; Check https://github.com/clojure/core.cache/wiki/Using
(defn remove-already-seen-tweets [seen tweets]
  (let [new-tweets (remove (fn [{:tweet/keys [id]}]
                             (contains? seen id))
                           tweets)]
    ;; (prn "DEBUG:: " tweets)
    ;; (prn "DEBUG:: " new-tweets)
    [new-tweets (apply conj seen (map :tweet/id new-tweets))]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [auth-handle (authenticate)]
    (loop [seen #{}] ;; `seen` is set of tweet ids
      (let [tweets (or (search auth-handle)
                       [])
            [new-tweets updated-seen] (remove-already-seen-tweets seen tweets)]
        (run! println (format-tweets new-tweets))
        (Thread/sleep sleep-time)
        (recur updated-seen)))))

(comment
  (def my-creds (api/authenticate twitter-creds))

  (search my-creds)

  (-main)

  )
