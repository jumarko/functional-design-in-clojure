(ns twitter.fetcher.processor-test
  (:require [clojure.test :refer [deftest is testing]]
            [twitter.fetcher.processor :as p]))

(deftest format-tweets
  (testing "no tweets"
    (is (= []
           (p/format-tweets []))))
  (testing "Tweets are properly formatted"
    (is (= [" * Juraj tweeted:\n      Check out Clojure - it's cool!"
            " * Joseph tweeted:\n      React is cool too!"]
           (p/format-tweets [{:user/name "Juraj" :tweet/text "Check out Clojure - it's cool!"}
                             {:user/name "Joseph" :tweet/text "React is cool too!"}
                             ]))))
  (testing "Newline is replaced with space"
    (is (= [" * Juraj tweeted:\n      First tweet line. Second tweet line"]
           (p/format-tweets [{:user/name "Juraj"
                              :tweet/text "First tweet line.\nSecond tweet line"}])))))

(deftest remove-already-seen-tweets
  (let [tweets [{:tweet/id "123" :tweet/text "OLDEST tweet" :user/name "Juraj"}
                {:tweet/id "456" :tweet/text "OLD tweet" :user/name "Joseph"}
                {:tweet/id "789" :tweet/text "NEW tweet" :user/name "Adam"}]]
    (testing "All tweets are returned new when the cache is empty"
      (is (= [tweets #{"123" "456" "789"}]
             (p/remove-already-seen-tweets #{} tweets))))
    (testing "Only new tweets are returned"
      (is (= [(drop 2 tweets) #{"123" "456" "789"}]
             (p/remove-already-seen-tweets #{"123" "456"} tweets)))))
  )
