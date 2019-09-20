(ns twitter.poster.twitter-api
  "Twitter api component providing means how to post and fetch tweets.
  Just a very simple wrapper around `twitter.api`."
  (:require [clojure.core.async :as a]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]))

(def twitter-creds (edn/read-string (slurp ".creds.edn")))


(defn- post-tweet [tweet]
  (log/info "Posting tweet to twitter: " tweet)
  ;; TODO: replace with actual API call
  (Thread/sleep (+ 500 (rand-int 1000)))
  (log/info "tweet posted to twitter: " tweet))

(defn- process-tweets [tweets-channel posted-tweets-channel]
  (a/go-loop []
    (let [tweet (a/<! tweets-channel)]
      (if tweet
        (do 
          (log/info "got tweet to be posted: " tweet)
          (a/thread
            (let [posted-tweet (post-tweet tweet)]
              (a/>! posted-tweets-channel posted-tweet)))
          (recur))
        (log/info "tweets-channel closed. Exit go-loop.")))))

;; TODO: Should this really be a component or is it enough to just use `twitter.api` directly??
;; can't think of reason why to introduce the full component right now
;; maybe the authentiation state (atom) could be handled here!
;; 
;; UPDATE: it seems that having this as a component and using channels to decouple worker
;; from actually posting the data introduces unnecessary complexity;
;; do we actually need one extra channel to communicate posted tweets back to worker?
;; or perhaps we would need to clutter twitter-api component with the db persistent logic?
;; => it doesn't look good and we should strive for simplicity
;;     and just use twitter-api as a library from the worker
;; MAYBE just make it a component but don't try to introduce extra channels
;; but actually use a protocol for TwitterApi?
;; (But this could be a classic example of a protocol growing every time we need a new operation)
(defrecord TwitterApi [tweets-to-post-channel posted-tweets-channel]
  component/Lifecycle
  (start [this]
    (process-tweets tweets-to-post-channel posted-tweets-channel))
  (stop [this]
    this))

(defn make-twitter-api []
  (map->TwitterApi {}))


