(ns twitter.poster.scheduler
  "A very simple scheduling component responsible for firing checking
  every minute (configurable?) whethere there are new tweets for posting."
  (:require [com.stuartsierra.component :as component]))

(defrecord Scheduler [scheduling-interval-ms scheduler-channel]
  component/Lifecycle
  (start [this]
    ;; TODO: start scheduler
    this
    )
  (stop [this]
    this))

(defn make-scheduler [scheduling-interval-ms]
  (map->Scheduler {:scheduling-interval-ms scheduling-interval-ms}))


