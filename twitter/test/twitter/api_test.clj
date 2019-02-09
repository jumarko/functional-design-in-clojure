(ns twitter.api-test
  (:require [twitter.api :as api]
            [clojure.test :refer [deftest is testing]]))

(def raw-statuses
  {:statuses [{:in_reply_to_screen_name nil,
               ,,,
               :quoted_status {:in_reply_to_screen_name nil,
                               :is_quote_status false,
                               ,,,
                               :entities {:hashtags [{:text "Clojure",
                                                      :indices [13 21]}],
                                          :symbols [],
                                          :user_mentions []},
                               :source "<a href=\"https://buffer.com\" rel=\"nofollow\">Buffer</a>",
                               :lang "en",
                               ,,,
                               :id_str "1072933859157790724",
                               :favorited false,
                               :user {,,,
                                      :name "stuarthalloway",
                                      ,,,
                                      :text "If you build #Clojure tooling, take advantage of the new error printing helpers on 1.10 https://t.co/5K9wX3CRpa"}}
               :id_str "1094338105144881152",
               :geo nil,
               :in_reply_to_status_id nil,
               :quoted_status_id_str "1072933859157790724",
               :user {:description "Helping developers become problem solvers. Cognitect, Clojure, Datomic.",
                      ,,,
                      :name "stuarthalloway",
                      :profile_background_image_url_https "https://abs.twimg.com/images/themes/theme1/bg.png",
                      :favourites_count 1168,
                      :screen_name "stuarthalloway",
                      ,,,
                      :id_str "14184390",
                      ,,,
                      :created_at "Thu Mar 20 14:38:29 +0000 2008"},
               :metadata {:iso_language_code "en", :result_type "recent"},
               :retweet_count 0,
               :favorite_count 0,
               :created_at "Sat Feb 09 20:55:01 +0000 2019",
               :text "If you have directly invoked the\n new error printing functions, I am curious to know where and how it worked for you… https://t.co/8XAOpPlj4c"}]})

(deftest tweets-to-internal-model
  (testing "Single tweet"
    (is (= [{:tweet/id "1094338105144881152"
             :tweet/text "If you have directly invoked the\n new error printing functions, I am curious to know where and how it worked for you… https://t.co/8XAOpPlj4c"
             :user/name "stuarthalloway"}]
           (api/raw->tweets raw-statuses)))))

