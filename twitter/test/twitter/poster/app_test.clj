(ns twitter.poster.app-test
  "Check `TwitterApiPoster` protocol."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [twitter.poster.app :as app]
            [twitter.poster.twitter-api :as twitter-api]))

(defrecord FakeApiPoster []
  twitter-api/TwitterApiPoster
  (post-tweet [this status-text]
    (log/info "Posting tweet: " status-text)))

(defn make-twitter-api-poster [] (->FakeApiPoster))


(defn fake-system
  "Create a dummy version of `app/new-system` using in-memory database and fake twitter-api.
  Runs a separate http server running on a special port dedicated to integration tests.
  Everything else is used as in normal 'live' mode."
  []
  (merge (app/new-system {:server-port 8088
                          :scheduler-interval-ms 2000})
         {:database (fn [] "TODO: in memory DB")
          :twitter-api-poster (make-twitter-api-poster)}))

(defn start-fake-system []
  (app/start-app (fake-system)))

(deftest smoke-test
  (let [system (start-fake-system)]
    (testing "Everything is up and running with fake components and in-memory database."
      (is (= []
             ())))
    (Thread/sleep 5000)
    (app/stop-app system)
  ))

