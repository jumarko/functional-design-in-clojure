(ns twitter.util.logging
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log])
  (:import (org.apache.logging.log4j Level LogManager)
           (org.apache.logging.log4j Level)))



(defn log-levels []
  (->> (Level/values)
       seq
       (map (fn [log-level]
              [(-> log-level str clojure.string/lower-case keyword)
               log-level]))
       (into {}))
  #_{:fatal Level/FATAL
   :error Level/ERROR
   :warn Level/WARN
   :info Level/INFO
   :debug Level/DEBUG
   :trace Level/TRACE
   :off Level/OFF
   :all Level/ALL}
  )

(s/def ::log-level (set (keys (log-levels))))
(s/fdef set-level!
  :args (s/cat :level ::log-level)
  :ret nil?)
(defn set-level!
  "Sets new log level for the Root logger. "
  [level]
  (if-let [log-level (get (log-levels) level)]
    ;; Using log4j2 API for setting log level: https://stackoverflow.com/a/23434603/1184752
    (let [logger-context (LogManager/getContext false)
          logger-config  (-> logger-context
                             .getConfiguration
                             (.getLoggerConfig LogManager/ROOT_LOGGER_NAME))]
      (.setLevel logger-config log-level)
      (.updateLoggers logger-context)
      ;; finally, we need to update logger-factory used internally by clojure.tools.logging
      ;; otherwise it would cache the log level set when it was initialized
      (alter-var-root #'log/*logger-factory* (fn [_] (clojure.tools.logging.impl/find-factory))))
    (throw (IllegalArgumentException. (str "Invalid log level: " level)))))


(comment
  (log/info "PRINTED.")

  (log/debug "NOT PRINTED")

  (set-level! :debug)
  (log/debug "PRINTED??")

  ;; check
  (clojure.tools.logging.impl/get-logger clojure.tools.logging/*logger-factory* *ns*)
  (clojure.tools.logging.impl/get-logger (clojure.tools.logging.impl/find-factory) *ns*)

  ;;
  )
