(ns twitter.poster.worker
  "A 'worker' component that listens for tweets to be put on the 'tweets-channel';
  it saves them into the DB for later processing.

  When the time comes (we're notified by `twitter.poster.scheduler` via a message containing current time),
  it fetches matching tweets from the database and attempts to post them to Twitter.
  It doesn't do any retries, just records results of the 'post attempt' in the database
  and waits until next message arrives.

  Design notes:
  - could be split into two separate components: listener for incoming tweets and actual 'Poster'.
  However, this doesn't seem to be necessary right now since the component should still be
  arguably simple."
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [next.jdbc.sql :as sql]
            [twitter.poster.date-utils :as date-utils]))

(defn- persist-tweet [db {:tweet/keys [text post-at] :as tweet}]
  (log/info "saving your tweet" tweet)
  (let [result (sql/insert! (db) "tweets" {:text text
                                           :post_at (.toInstant post-at)})]
    (log/info "I saved your tweet: " result))
  )

(defn- persist-tweets [tweets-channel db]
  (a/go-loop []
    (let [tweet (a/<! tweets-channel)]
      (if tweet
        (do
          (log/info "got new tweet: " tweet)
          ;; TODO: persist-tweet operation should be async to avoid blocking core.async thread pool
          ;; is `a/thread` the proper mechanism to use? Check the implementation; does it actually block
          ;;   if the operation returns a non-nil value until it's consumed?!?
          ;; Also check this: https://clojureverse.org/t/to-block-or-not-to-block-in-go-blocks/2104/10?u=jumar
          ;;  - Similarly, you should be very careful with anything that creates a new thread per channel value. ... it throws away backpressure.
          ;;    ... You should instead spin up a fixed number of threads and let them handle each value as they can. 
          ;;    => Don't use a/thread inside a go block!?
          (a/thread (persist-tweet db tweet))
          (recur))
        (log/info "tweets-channel closed. Exit go-loop.")))))

(defn db-tweet->tweet [{:TWEETS/keys [ID TWEET_ID TEXT POST_AT POSTED_AT]}]
  (cond-> #:tweet{:id ID
                  :text TEXT
                  :post-at (date-utils/sql-timestamp->zoned-date-time POST_AT)}
    TWEET_ID (assoc :tweet/tweet-id TWEET_ID)
    POSTED_AT (assoc :tweet/posted-at POSTED_AT)))

(defn- select-tweets-for-posting [db time-now]
  (let [db-tweets (sql/query
                  (db)
                  ["select * from tweets WHERE tweet_id is NULL and post_at < ? order by post_at"
                   time-now])]
    (mapv db-tweet->tweet db-tweets)))

(defn update-posted-tweet
  [db {:tweet/keys [id tweet-id posted-at] :as posted-tweet}]
  ;; TODO: update tweet in DB (tweet_id and posted_at (column to be added))
  (log/info "Saving posted tweet to DB: " posted-tweet)
  (let [result (sql/update! (db)
                            "tweets"
                            {:tweet_id tweet-id
                             :posted_at posted-at}
                            {:id id})]
    (log/info "Tweet updated: " result)))

(defn- update-posted-tweets
  "Given external tweet ID of posted tweets, it's now time
  to save this data in the DB so they don't get posted again.

  TODO: it could happen that the thread doing the API post is actually too slow
  and the next scheduler cycle will come before the DB is updated.
  Shall we take care of that and prevent duplicate tweets to be posted?"
  [posted-tweets-channel db]
  (a/go-loop []
    (let [tweet (a/<! posted-tweets-channel)]
      (if tweet
        (do
          (log/info "got posted tweet: " tweet)
          (a/thread (update-posted-tweet db tweet))
          (recur))
        (log/info "posted-tweets-channel closed. Exit go-loop.")))))

(defn- handle-process-tweets [db time-now]
  (let [tweets-to-post (select-tweets-for-posting db time-now)]
    (log/info "Tweets selected for posting: " tweets-to-post)
    tweets-to-post))

(defn- post-tweets [scheduler-channel db tweets-to-post-channel]
  (a/go-loop []
    (let [time-now (a/<! scheduler-channel)]
      ;; exit the loop if the scheduler-channel has been closed
      (when time-now
        (a/thread
          (let [tweets-to-post (handle-process-tweets db time-now)]
            ;; don't close the channel, just wait for another scheduler round
            ;; TODO: what's the difference between using `a/onto-chan`
            ;; and putting onto a channel manually one-by-one using `a/>!` ?
            (a/onto-chan tweets-to-post-channel tweets-to-post false)))
        (recur)))))

(defrecord Worker [tweets-channel scheduler-channel database tweets-to-post-channel posted-tweets-channel]
  component/Lifecycle
  (start [this]
    (persist-tweets tweets-channel database)
    (post-tweets scheduler-channel database tweets-to-post-channel)
    (update-posted-tweets posted-tweets-channel database)
    this)
  (stop [this]
    this))

(defn make-worker []
  (map->Worker {}))

(comment
  (persist-tweet
   (:database twitter.poster.app/my-app)
   #:tweet{:text "My First Tweet"
           :post-at (java.time.ZonedDateTime/parse "2019-09-06T11:40:00+02:00")})

  ;; notice that we truly get the timezone from the database (+02:00)
  ;; it's not just about default java timezone settings
  (sql/query
   ((:database twitter.poster.app/my-app))
   ["select * from tweets order by post_at desc"])

  

  (select-tweets-for-posting
   (:database twitter.poster.app/my-app)
   (.toInstant (java.time.ZonedDateTime/of 2019 9 6 10 39 0 0 java.time.ZoneOffset/UTC)))
;; => [#:TWEETS{:ID 4,
;;              :TWEET_ID nil,
;;              :TEXT "My First Tweet",
;;              :POST_AT #object[org.h2.api.TimestampWithTimeZone 0x1246964d "2019-09-06 11:40:00+02"]}]  ;;


  ;; DELETE ALL DATA IF YOU WANT!
  (next.jdbc/execute!
   ((:database twitter.poster.app/my-app))
   ["delete from tweets"])

  ;; OR EVEN DROP THE TABLE!
  (next.jdbc/execute!
   ((:database twitter.poster.app/my-app))
   ["drop table tweets"])

  ;; 
  )


