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
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as a]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]))

(defn- save-to-db [db {:tweet/keys [text post-at] :as tweet}]
  (println "I saved your tweet" tweet)
  #_(sql/insert! (db) "tweets" {:text text
                              :post_at post-at}))
(defn- persist-tweet [db tweet]
  (save-to-db db tweet))

(defn- persist-tweets [db tweets-channel]
  (a/go-loop []
    (let [tweet (a/<! tweets-channel)]
      ;; TODO: persist-tweet operation should be async to avoid blocking core.async thread pool
      ;; is `a/thread` the proper mechanism to use? Check the implementation; does it actually block
      ;;   if the operation returns a non-nil value until it's consumed?!?
      ;; Also check this: https://clojureverse.org/t/to-block-or-not-to-block-in-go-blocks/2104/10?u=jumar
      ;;  - Similarly, you should be very careful with anything that creates a new thread per channel value. ... it throws away backpressure.
      ;;    ... You should instead spin up a fixed number of threads and let them handle each value as they can. 
      ;;    => Don't use a/thread inside a go block!?
      a/pipeline-blocking
      (a/thread persist-tweet db tweet)
      (recur))))

(defn- select-tweets-for-posting [db time-now]
  [{:text "Dummy tweet"
    :post_at time-now}]
  #_(sql/query (db) ["select * from tweets"]))

(defn- process-tweets [db scheduler-channel]
  (a/go-loop []
    (let [time-now (a/<! scheduler-channel)
          ;; TODO: should be async IO
          tweets-to-post (select-tweets-for-posting db time-now)]
      (println "Following tweets selected for posting:" tweets-to-post)
      (recur))))

;; TODO: implement
(defrecord Worker [tweets-channel scheduler-channel db]
  component/Lifecycle
  (start [this]
    (persist-tweets db tweets-channel)
    (process-tweets db scheduler-channel)
    this)
  (stop [this]
    this))

(defn make-worker []
  (map->Worker {}))