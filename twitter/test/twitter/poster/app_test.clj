(ns twitter.poster.app-test
  "Check `TwitterApiPoster` protocol."
  (:require [clj-http.client :as http]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [twitter.poster.app :as app]
            [twitter.poster.db :as db]
            [twitter.poster.twitter-api :as twitter-api])
  (:import java.util.UUID))

(def testing-port 8088)
(def testing-scheduler-interval 1000)

(defrecord FakeApiPoster []
  twitter-api/TwitterApiPoster
  (post-tweet [this status-text]
    (log/info "FAKER: Pretending that posting tweet: " status-text)
    {:id_str (str "POSTED-" (UUID/randomUUID))}))

(defn make-twitter-api-poster [] (->FakeApiPoster))

;; https://github.com/seancorfield/next-jdbc/blob/master/test/next/jdbc/test_fixtures.clj
(defn in-memory-db-spec []
  {:dbtype "h2:mem"
   :dbname (str "twitter_poster_h2_mem_" (UUID/randomUUID))})

(defn fake-system
  "Create a dummy version of `app/new-system` using in-memory database and fake twitter-api.
  Runs a separate http server running on a special port dedicated to integration tests.
  Everything else is used as in normal 'live' mode."
  []
  ;; this config handling should be elsewhere?
  (let [config (merge (edn/read-string (slurp ".creds.edn"))
                      {:server-port testing-port
                       :scheduler-interval-ms testing-scheduler-interval})]
    (merge (app/new-system config)
           {:database (db/make-database {:db-spec (in-memory-db-spec)})
            :twitter-api-poster (make-twitter-api-poster)})))

(defn start-fake-system []
  (app/start-app (fake-system)))


(defn testing-url [path]
  (format "http://localhost:%d/%s" testing-port path))

(deftest smoke-test
  (let [system (start-fake-system)]
    ;; curl -H 'Content-Type: application/json' -X POST -v localhost:8082/tweets \
    ;;   -d '{"text" : "My refactored Tweet", "post-at": "2019-09-06T11:40:00+02:00"}'
    (try

      (testing "Everything is up and running with fake components and in-memory database."
        (let [{:keys [status body] :as post-response}
              (http/post (testing-url "tweets")
                         {:content-type :json
                          :as :json
                          ;; ends up in the request body
                          :form-params {:text "My testing tweet" :post-at "2019-09-06T11:40:00+02:00"}
                          :conn-timeout 1000
                          :socket-timeout 1000})]
          (prn post-response)
          (is (= 202 status))
          (is (= "accepted" (:state body)))))
      ;; Sleep a while and check the tweet status
      (Thread/sleep (* 2 testing-scheduler-interval))
      (let [tweets (-> (http/get (testing-url "tweets")
                                 {:as :json})
                       :body)]
        (is (= #{{:tweet/text "My testing tweet"
                  ;; it's the limitation of datetime handling in the application that we only return
                  ;; dates in UTC (but the conversion should be correct!)
                  :tweet/post-at "2019-09-06T09:40:00Z"}}
               (clojure.set/project tweets [:tweet/post-at :tweet/text]))))
      (finally
        (app/stop-app system)))))

