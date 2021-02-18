(ns twitter.poster.worker-test
  (:require [twitter.poster.worker :as worker]
            [clojure.test :refer [deftest is testing]]))

(def db-tweet {:TWEETS/ID 1,
               :TWEETS/TWEET_ID "POSTED-e2a18185-9131-467f-92a2-fc6de3965370",
               :TWEETS/TEXT "My testing tweet",
               :TWEETS/POST_AT #inst "2019-10-01T00:00:00+00:00",
               :TWEETS/POSTED_AT #inst "2019-10-01T11:16:36.530040000-00:00"})

(deftest db-tweet->tweet
  (testing "conversion is done properly including timestamps"
    (is (=
         {:tweet/id 1
          :tweet/tweet-id "POSTED-e2a18185-9131-467f-92a2-fc6de3965370",
          :tweet/text "My testing tweet"
          :tweet/post-at  #inst "2019-10-01T00:00:00+00:00"
          :tweet/posted-at #inst "2019-10-01T11:16:36.530040000-00:00"}
         (worker/db-tweet->tweet db-tweet)))))


