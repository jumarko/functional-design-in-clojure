(ns twitter.poster.routes
  (:require [clojure.core.async :as a]
            [clojure.spec.alpha :as s]
            [compojure.core :refer [GET POST routes context]]
            [next.jdbc.sql :as sql]
            [twitter.poster.worker :as worker]
            [twitter.poster.date-utils :as date-utils]))

;;; TODO 




;; You can test with CURL
;;   curl -H 'Content-Type: application/json' -X POST -v localhost:8082/tweets -d '{"text" : "My First Tweet", "post-at": "2019-09-06T11:40:00+02:00"}'

;; to demonstrate potential Time Zone issues
(comment

  
  ;; here we get only offset => problems with day-light saving or timezone changes
  (.getZone (java.time.ZonedDateTime/parse "2019-09-06T11:40:00+02:00"))
  ;; => #object[java.time.ZoneOffset 0x75c96f61 "+02:00"]

  ;; here we get full info => should be fine to use but at DB level we only use OffsetDateTime
  (.getZone (java.time.ZonedDateTime/parse "2019-09-06T11:40:00+02:00[Europe/Paris]"))
  ;; => #object[java.time.ZoneRegion 0xd234fe4 "Europe/Paris"]

  ;;
  )

(defn- get-tweets [db]
  (let [db-tweets (sql/query
                   (db)
                   ["select * from tweets order by post_at desc"])
        ;; cheshire doesn't know how to convert ZonedDateTime to JSON so we need to actually
        ;; convert it to Instant
        tweets (mapv (fn [tweet]
                       (-> tweet
                           worker/db-tweet->tweet
                           (update :tweet/post-at date-utils/zoned-date-time->date)))
                     db-tweets)]
    (prn "DEBUG:: tweets " tweets)
    {:status 200
     :body tweets
     :headers {"Content-Type" "application/json"}}))

(defn- schedule-tweet [tweets-channel request]
  ;; TODO validate tweet API request data, especially `post-at`
  ;; and return 400 if invalid -> should happen before throwing exception by ZonedDateTime/parse
  ;; spec and conform could be used for this
  (let [{:keys [text post-at]} (:body request)
        transformed-tweet {:tweet/text text
                           ;; Note (maybe TODO ?): although we use ZonedDateTime it's later converted to
                           ;; OffsetDateTime and thus we actually lose the zone info
                           ;; which could cause problems because of day-light saving and timezone changes 
                           :tweet/post-at (java.time.ZonedDateTime/parse post-at)}]
    (if-not (s/valid? :tweet/tweet transformed-tweet)
      {:status 400
       :body (str "Bad data: " (s/explain-str :tweet/tweet transformed-tweet))}

      ;; backpressure? - blocking unless there's some space in the buffer?
      ;; Here we  use the non-blocking put inside and just "acknowledge" the HTTP POST operation
      ;; without actually "scheduling" anything
      ;; this will throw an assertion error if there are more than 1024 outstanding errors
      ;; which is basically fine for our purpose -> client will get an error and he will need to slow down
      (do
        (a/>!! tweets-channel transformed-tweet)
        {:status 202
         :body {:state "accepted"}
         :headers {"Contet-Type" "application/json"}}))))

(defn make-routes [tweets-channel db]
  (context "/tweets" []
    (POST "/" req (schedule-tweet tweets-channel req))
    (GET "/" req (get-tweets db))))


(comment

  (get-tweets (:database twitter.poster.app/my-app))
  ;;=>
  {:status 200,
   :body
   [#:tweet{:id 1,
            :text "My First Tweet",
            :post-at #inst "2019-09-06T09:40:00.000-00:00",
            :tweet-id "1-duplicate"}],
   :headers {"Content-Type" "application/json"}}

 )
