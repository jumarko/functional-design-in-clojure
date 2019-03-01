(ns twitter.processor
  "Processor is a terrible name but this namespace capture the never-ending process of
  fetching tweets (via `twitter.api`) and printing them into console.
  The whole process then sleeps and is repeated over and over again,
  possibly with an updated search string."
  (:require

   [clojure.repl :refer [pst]]
   [twitter.api :as api]))

(defn- search [auth-state query]
  (try
    (api/search auth-state query)
    (catch Exception e
      ;; TODO: it might be too late to catch data here
      ;; since we would need reponse boy if non-ok HTTP status is thrown
      ;; => if we don't do that in `twitter.api` ns then http client details will leak
      (pst e))))

(defn format-tweets [tweets]
  (let [format-tweet (fn format-tweet [tweet]
                       (format " * %s tweeted:\n      %s"
                               (:user/name tweet)
                               (some-> tweet :tweet/text (clojure.string/replace "\n" " "))))]
    (if (empty? tweets)
      []
      (mapv format-tweet tweets))))

;; TODO: use proper cache (evicting old entries to prevent out of memory)
;; Check https://github.com/clojure/core.cache/wiki/Using
(defn remove-already-seen-tweets [seen tweets]
  (let [new-tweets (remove (fn [{:tweet/keys [id]}]
                             (contains? seen id))
                           tweets)]
    ;; (prn "DEBUG:: " tweets)
    ;; (prn "DEBUG:: " new-tweets)
    [new-tweets (apply conj seen (map :tweet/id new-tweets))]))

(defn process-tweets
  "Gets new tweets, prints them, and returns a tuple [updated-auth-state updated-seen].
  This is a single step in a never-ending loop."
  [query auth-state seen]
  (let [[updated-auth-state tweets] (or (search auth-state query)
                                        [auth-state []])
        [new-tweets updated-seen] (remove-already-seen-tweets seen tweets)]
    (run! println (format-tweets new-tweets))
    [updated-auth-state updated-seen]))

