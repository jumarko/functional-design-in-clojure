(ns twitter.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [twitter.core :as c]))

(deftest format-tweet
  (testing "Tweet is properly formatted"
    (is (= "Juraj tweeted: 'Check out Clojure - it's cool!'\n"
         (c/format-tweet {:user/name "Juraj"
                            :tweet/text "Check out Clojure - it's cool!"}))))
  (testing "Newline is replaced with space"
    (is (= "Juraj tweeted: 'First tweet line. Second tweet line'\n"
           (c/format-tweet {:user/name "Juraj"
                            :tweet/text "First tweet line.\nSecond tweet line"})))))
