(ns twitter.poster.scheduler
  "A very simple scheduling component responsible for sending current time (as `java.time.Instan`)
  on given  channel using configured interval."
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as a]
            [clojure.tools.logging :as log]))

(defn- start-scheduler [interval-ms scheduler-channel]
  (a/go-loop []
    (let [timeout-ch (a/timeout interval-ms)]
      (a/<! timeout-ch)
      ;; exit the loop if `scheduler-channel` has been closed
      (let [time-now (java.time.Instant/now)]
        (when (a/>! scheduler-channel time-now)
          (log/debug "Scheduler fired at: " time-now)
          (recur))))))

(defrecord Scheduler [scheduling-interval-ms scheduler-channel]
  component/Lifecycle
  (start [this]
    (start-scheduler scheduling-interval-ms scheduler-channel)
    this
    )
  (stop [this]
    ;; we can slose the channel here too but it's better close all channels in app.clj (already done)
    (a/close! scheduler-channel)
    this))

(defn make-scheduler [scheduling-interval-ms]
  (map->Scheduler {:scheduling-interval-ms scheduling-interval-ms}))


