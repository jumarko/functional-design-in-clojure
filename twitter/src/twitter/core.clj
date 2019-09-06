(ns twitter.core
  "Main namespace starting an infinite loop waiting for twitter queries
  and searching for matching tweets in pre-defined intervals.
  NOTE: This is the original core namespace for application developed for episodes 6 - 11.
  The `twitter.poster.app` ns contains the Twitter Poster app developed for episodes 21 - 27."
  (:require
   [clojure.core.async :as async]
   [com.stuartsierra.component :as component]
   [twitter.fetcher :as fetcher]
   [twitter.server :as server]))

(defn new-system
  []
  (component/system-map
   :query-channel (async/chan 10)
   :server (component/using (server/new-component) [:query-channel])
   :fetcher (component/using (fetcher/new-component) [:query-channel])))

(defn -main
  "Runs the main loop polling for tweets."
  [& args]
  ;; TODO: exception handler?
  (let [system (component/start (new-system))]
    system))

;; this is from shownotes: https://clojuredesign.club/episode/010-from-mud-to-bricks/
;; (defn -main
;;   [& args]
;;   (let [system (component/start (new-system))
;;         lock (promise)
;;         stop (fn []
;;                (component/stop system)
;;                (deliver lock :release))]
;;     (.addShutdownHook (Runtime/getRuntime) (Thread. stop))
;;     @lock
;;     (System/exit 0)))

(comment

  (api/search fetcher/twitter-creds "#clojure")

  (def main-system (-main))

  (component/stop main-system)


  )
