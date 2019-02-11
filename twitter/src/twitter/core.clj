(ns twitter.core
  (:require
   [clojure.edn :as edn]
   [clojure.repl :refer [pst]]
   [twitter.api :as api]))

(def twitter-creds (edn/read-string (slurp ".creds.edn")))

(defn- authenticate []
  (api/authenticate twitter-creds))

(defn- search [auth-handle]
  (api/search auth-handle "#clojure"))

(defn format-tweet [tweet]
  (format "%s tweeted: '%s'\n"
          (:user/name tweet)
          (-> tweet :tweet/text (clojure.string/replace "\n" " "))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [auth-handle (authenticate)]
    (loop []
      (try
        (let [tweets (search auth-handle)]
          ;; TODO: dedup tweets
          (run! println (mapv format-tweet tweets)))
        (catch Exception e
          ;; TODO: it might be too late to catch data here
          ;; since we would need reponse boy if non-ok HTTP status is thrown
          ;; => if we don't do that in `twitter.api` ns then http client details will leak
          (pst e)))
      (Thread/sleep 15000)
      (recur))))

(comment
  (def my-creds (api/authenticate twitter-creds))

  (search my-creds)

  (-main)

  )
