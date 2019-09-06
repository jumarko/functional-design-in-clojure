(ns twitter.poster.twitter-api
  "Twitter api component providing means how to post and fetch tweets.
  Just a very simple wrapper around `twitter.api`."
  (:require [com.stuartsierra.component :as component]))

;; TODO: Should this really be a component or is it enough to just use `twitter.api` directly??
;; can't think of reason why to introduce the full component right now
;; maybe the authentiation state (atom) could be handled here!
(defrecord TwitterApi []
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    this))

(defn make-twitter-api []
  (map->TwitterApi {}))


