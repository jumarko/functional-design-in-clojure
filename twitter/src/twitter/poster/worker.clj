(ns twitter.poster.worker
  "A 'worker' component that listens for tweets to be scheduled comming on a channel;
  it saves them into the DB for later processing.

  When the time comes (we're notified by `twitter.poster.scheduler` via message containing current time),
  it fetches matching tweets from the database and attempts to post them to Twitter.
  It doesn't do any retries, just records results of the 'post attempt' in the database
  and waits until next message arrives.

  Design notes:
  - could be split into two separate components: listener for incoming tweets and actual 'Poster'.
  However, this doesn't seem to be necessary right now since the component should still be
  arguably simple."
  (:require [com.stuartsierra.component :as component]))

;; TODO: implement
(defrecord Worker [tweets-channel scheduler-channel]
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    this))

(defn make-worker []
  (map->Worker {}))
