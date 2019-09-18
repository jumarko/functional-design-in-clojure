(ns twitter.poster.routes
  (:require [clojure.core.async :as a]
            [clojure.spec.alpha :as s]
            [compojure.core :refer [POST routes]]))

;;; TODO 




;; You can test with CURL
;;   curl -H 'Content-Type: application/json' -X POST -v localhost:8082/tweets -d '{"text" : "My First Tweet", "post-at": "2019-09-06T11:40:00+02:00"}'


(defn- schedule-tweet [tweets-channel request]
  ;; TODO validate tweet API request data, especially `post-at`
  ;; and return 400 if invalid -> should happen before throwing exception by ZonedDateTime/parse
  ;; spec and conform could be used for this
  (let [{:keys [text post-at]} (:body request)
        transformed-tweet {:tweet/text text
                           :tweet/post-at (java.time.ZonedDateTime/parse post-at)}]
    (if-not (s/valid? :tweet/tweet transformed-tweet)
      {:status 400
       :body (str "Bad data: " (s/explain-str :tweet/tweet transformed-tweet))}

      ;; backpressure? - blocking unless there's some space in the buffer??
      ;; TODO: which version of 'put' use?
      ;; is it ok to block the request when the channel buffer is full? ('backpressure' ?)
      ;; Alternatively, we could use non-blocking put inside go and just acknowledge the HTTP POST
      ;; operation without actually knowing it's finished
      (do (a/>!! tweets-channel transformed-tweet)
          {:status 201
           :body {:status "scheduled"}
           :headers {"Contet-Type" "application/json"}}))))

(defn make-routes [tweets-channel]
  (routes (POST "/tweets" req (schedule-tweet tweets-channel req))))

