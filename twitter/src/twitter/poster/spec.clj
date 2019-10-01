(ns twitter.poster.spec
  (:require [clojure.spec.alpha :as s]))


;; ZonedDateTime should be fine as a date/time representation because what we really want to do
;; is to schedule a tweet to be posted in user's timezone
;; However, there's a problem with storing it in the DB (only OffsetDateTime is stored)
(s/def ::date-time (partial instance? java.time.ZonedDateTime))

;; here I intentionally left out specs for simple attributes which are obvious
;; to experiment if they would bring some benefits or not
(s/def :tweet/text (and string? (complement clojure.string/blank?)))
(s/def :tweet/id int?)
(s/def :tweet/tweet-id string?) ; external Twitter ID
(s/def :tweet/posted-at ::date-time)

(s/def :tweet/post-at ::date-time)

;; TODO: attributes like post-at are really specific to scheduling and not shared in all use cases
;; => maybe it should be separated and provided as a different param of `schedule-tweet` fn?
(s/def :tweet/tweet (s/keys :req [:tweet/text :tweet/post-at]
                                    ;; IDs and posted? are only filled once the tweet is posted
                                    ;; TODO: the disinction between db-id and tweet-it looks awkward
                                    :opt [:tweet/id
                                          :tweet/tweet-id
                                          :tweet/posted-at]))
