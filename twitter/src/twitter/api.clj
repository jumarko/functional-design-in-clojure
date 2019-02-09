(ns twitter.api
  "Fetches data from twitter.
  We use standard API which returns tweets only for the past 7 days
  and doesn't guarantee completeness.

  We use Aleph as http client: https://github.com/ztellman/aleph

  See Twitter docs:
  - https://developer.twitter.com/en/docs/basics/getting-started
  - https://developer.twitter.com/en/docs/tweets/search/overview/standard"
  (:require
   [aleph.http :as http]
   [byte-streams :as bs]))

(def twitter-api-root "https://api.twitter.com/1.1")

(defn fetch [path]
  ;; TODO: catch http exceptions and retrieve response body if applicable
  @(http/get (str twitter-api-root path))
  )

(defn search
  "Given query returns all matching tweets via Twitter API"
  [query]
  (fetch "/search/tweets.json")
  )

(comment

  (try 
    (fetch "/search/tweets.json")
    (catch Exception e
        (some-> e ex-data :body bs/to-string)))


 
  )
