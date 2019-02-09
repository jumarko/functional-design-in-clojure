(ns twitter.core
  (:require
   [clojure.repl :refer [pst]]
   [twitter.api :as api]))

(defn- search []
  (api/search "#clojure"))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (loop []
    (try
      (let [tweets (search)]
        ;; TODO: dedup tweets
        (prn tweets)
        )
      (catch Exception e
        ;; TODO: it might be too late to catch data here
        ;; since we would need reponse boy if non-ok HTTP status is thrown
        ;; => if we don't do that in `twitter.api` ns then http client details will leak
        (pst e)))
    (Thread/sleep 15000)
    (recur))
  )

(comment

  (search)

  (-main)

  )
