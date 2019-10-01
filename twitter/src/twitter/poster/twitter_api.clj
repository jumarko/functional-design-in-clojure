(ns twitter.poster.twitter-api
  "Twitter api component providing means how to post and fetch tweets.
  Just a very simple wrapper around https://github.com/chbrown/twttr"
  (:require [clojure.core.async :as a]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [twttr.api :as api]
            [twttr.auth :as auth]
            [clojure.spec.alpha :as s]))

(defprotocol TwitterApiPoster
  "A simple protocol for posting tweets via Twitter API.
  Used also for faking the real twitter API in integration tests - see `twitter.poster.app-test`."
  (post-tweet [this status-text]
    "Posts given tweet using provided credentials to authenticate against the API.
    Should return tweet response data, in particular the `id_str` key is expected to contain an ID
    assigned by Twitter.
    This ID is later used to distinguish which tweets have already been posted and which not (in worker.clj)."))

;; https://github.com/chbrown/twttr#example
(s/def ::twitter-creds (s/keys :req-un [::consumer-key ::consumer-secret ::user-token ::user-token-secret]))
(s/fdef twitter-creds
  :args (s/cat ::creds ::twitter-creds))
(defn twitter-creds [config]
  (auth/map->UserCredentials config))

(defn- status-is-duplicate-error? [response-errors-data]
  (= 187
     (-> response-errors-data first :code)))

;; TODO: where/how to handle errors?
;; In particular, "Status is a duplicate" is an interesting error response:
;;   :status 403,
;;   :body {:errors [{:code 187, :message "Status is a duplicate."}]}}
(s/def ::api-poster #(satisfies? TwitterApiPoster %))
(s/fdef process-tweet
  :args (s/cat :api-poster ::api-poster
               :tweet :tweet/tweet))
(defn- process-tweet [api-poster {:tweet/keys [id text] :as tweet}]
  (log/info "Posting tweet to twitter: " tweet)
  (try
    (let [raw-tweet-data (post-tweet api-poster text)
          ;; _ (prn "DEBUG:: raw-tweet-data " raw-tweet-data)
          posted-tweet (assoc tweet
                              :tweet/tweet-id (:id_str raw-tweet-data)
                              :tweet/posted-at (java.time.Instant/now))]
      (log/info "tweet posted to twitter: " posted-tweet)
      posted-tweet)
    (catch Exception e
      (let [response-errors (some-> (ex-data e) :body :errors)]
        (if (status-is-duplicate-error? response-errors)
          (do
            (log/warn "Looks like the status has already been posted - got an error from the twitter api: "
                      response-errors)
            ;; just provide some fake external id to make sture we don't select the same tweet again
            ;; (see `worker/select-tweets-for-posting`)
            (assoc tweet :tweet/tweet-id (str id "-duplicate")))
          ;; just rethrow and try again later
          (throw e))))))

(defn- process-tweets [api-poster tweets-channel posted-tweets-channel]
  (a/go-loop []
    (let [tweet (a/<! tweets-channel)]
      (if tweet
        (do 
          (log/info "got tweet to be posted: " tweet)
          (a/thread
            (let [posted-tweet (process-tweet api-poster tweet)]
              ;; TODO: cannot use a/>! from a/thread
              ;; but we'd like to avoid blocking current thread if there's nobody on the receiving end
              ;; => maybe just proper buffer sizes?
              (a/put! posted-tweets-channel posted-tweet)))
          (recur))
        (log/info "tweets-channel closed. Exit go-loop.")))))

(defn- twttr-post-tweet [creds status-text]
  (api/statuses-update creds :params {:status status-text}))

;;; Implementation of `TwitterApiPoster` using `twttr.api`
;;; This is the real impl used for posting live tweets
(defrecord TwttrApiPoster [credentials]
  TwitterApiPoster
  (post-tweet [this status-text]
    (twttr-post-tweet credentials status-text))
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(defn make-twitter-api-poster [config]
  (map->TwttrApiPoster {:credentials (twitter-creds config)}))

;; TODO: Should this really be a component or is it enough to use `twttr.api` directly??
;; can't think of reason why to introduce the full component right now
;; maybe the authentiation state (atom) could be handled here!
;; 
;; UPDATE: it seems that having this as a component and using channels to decouple worker
;; from actually posting the data introduces unnecessary complexity;
;; do we actually need one extra channel to communicate posted tweets back to worker?
;; or perhaps we would need to clutter twitter-api component with the db persistent logic?
;; => it doesn't look good and we should strive for simplicity
;;     and just use twitter-api as a library from the worker
;; MAYBE just make it a component but don't try to introduce extra channels
;; but actually use a protocol for TwitterApi?
;; (But this could be a classic example of a protocol growing every time we need a new operation)
(defrecord TwitterApi [tweets-to-post-channel posted-tweets-channel twitter-api-poster]
  component/Lifecycle
  (start [this]
    (process-tweets twitter-api-poster tweets-to-post-channel posted-tweets-channel)
    this)
  (stop [this]
    this))

(defn make-twitter-api []
  (map->TwitterApi {}))


(comment

  (def creds (edn/read-string (slurp ".creds.edn")))
  (post-tweet creds {:tweet/text "clojure is so fun!"})

  ;; example of raw tweet data
  {:in_reply_to_screen_name nil,
 :is_quote_status false,
 :coordinates nil,
 :in_reply_to_status_id_str nil,
 :place nil,
 :geo nil,
 :in_reply_to_status_id nil,
 :entities {:hashtags [], :symbols [], :user_mentions [], :urls []},
 :source
 "<a href=\"https://github.com/jumarko/functional-design-in-clojure\" rel=\"nofollow\">fun-clojure-twitter-poster</a>",
 :lang "pt",
 :in_reply_to_user_id_str nil,
 :id 1176426021064708096,
 :contributors nil,
 :truncated false,
 :retweeted false,
 :in_reply_to_user_id nil,
 :id_str "1176426021064708096",
 :favorited false,
 :user
 {:description "",
  :profile_link_color "1DA1F2",
  :profile_sidebar_border_color "C0DEED",
  :is_translation_enabled false,
  :profile_image_url
  "http://abs.twimg.com/sticky/default_profile_images/default_profile_normal.png",
  :profile_use_background_image true,
  :default_profile true,
  :profile_background_image_url nil,
  :is_translator false,
  :profile_text_color "333333",
  :name "Juraj Pure Bot",
  :profile_background_image_url_https nil,
  :favourites_count 0,
  :screen_name "JurajBot",
  :entities {:description {:urls []}},
  :listed_count 0,
  :profile_image_url_https
  "https://abs.twimg.com/sticky/default_profile_images/default_profile_normal.png",
  :statuses_count 7,
  :has_extended_profile false,
  :contributors_enabled false,
  :following false,
  :lang nil,
  :utc_offset nil,
  :notifications false,
  :default_profile_image true,
  :profile_background_color "F5F8FA",
  :id 978922981580828672,
  :follow_request_sent false,
  :url nil,
  :translator_type "none",
  :time_zone nil,
  :profile_sidebar_fill_color "DDEEF6",
  :protected false,
  :profile_background_tile false,
  :id_str "978922981580828672",
  :geo_enabled false,
  :location "",
  :followers_count 0,
  :friends_count 0,
  :verified false,
  :created_at "Wed Mar 28 09:13:21 +0000 2018"},
 :retweet_count 0,
 :favorite_count 0,
 :created_at "Tue Sep 24 09:19:55 +0000 2019",
 :text "clojure is so fun!"}

  )
