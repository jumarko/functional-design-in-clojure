(ns twitter.api
  "Fetches data from twitter.
  We use standard API which returns tweets only for the past 7 days
  and doesn't guarantee completeness.

  We use Aleph as http client: https://github.com/ztellman/aleph
  - check https://github.com/dakrone/clj-http for supported request map params.

  See Twitter docs:
  - https://developer.twitter.com/en/docs/basics/getting-started
  - https://developer.twitter.com/en/docs/tweets/search/overview/standard"
  (:require
   [aleph.http :as http]
   [byte-streams :as bs]
   [clojure.spec.alpha :as s]))

;; credentials in special local-only file for now
(def twitter-api-root-url "https://api.twitter.com")
(def twitter-api-url (str twitter-api-root-url "/1.1"))

(defn api-url [relative-url]
  (str twitter-api-url relative-url))

(defn response-body
  "Returns response body, counts on using `:as :json` in the request map"
  [response]
  (some-> response :body))

(defn body->string [response]
  (bs/to-string response))

(defn- handle-errors [request-fn]
  (try
    (request-fn)
    (catch clojure.lang.ExceptionInfo e
      (if-let [body-text (some-> e ex-data response-body body->string)]
        (throw (ex-info (ex-message e) (-> e ex-data (assoc :body body-text))))
        (throw e)))))

(defn fetch
  "Fetches data from given api resource (`path`)
  by using given authentication handle and adding optional extract `request-options`."
  ([handle path]
   (fetch handle path {}))
  ([handle path request-options]
   (handle-errors
    (fn fetch-fn [] 
      (-> (http/get (api-url path)
                 (merge 
                  {:oauth-token (:token handle)
                   :as :json}
                  request-options))
          deref
          response-body)))))

(defn authenticate
  "Authenticates using consumer's api key and secret
  and returns authentication handle (token).
  See https://developer.twitter.com/en/docs/basics/authentication/api-reference/token.html"
  [creds]
  (handle-errors
   (fn auth-fn []
     (let [response-body (response-body @(http/post (str twitter-api-root-url "/oauth2/token")
                                            {:basic-auth [(:api-key creds) (:api-secret creds)]
                                             :as :json
                                             :content-type :json
                                             :query-params {:grant_type "client_credentials"}}))]
       (if-let [token (:access_token response-body)]
         {:token token}
         (throw (ex-info "Unexpected response - missing access token" {:body response-body})))))))

(s/def :twitter/tweet (s/keys :req [:tweet/id :tweet/text :user/name]))

(defn- raw->tweet [{id :id_str
                    text :text
                    {username :name} :user}]
  {:tweet/id id
   :tweet/text text
   :user/name username})

(defn raw->tweets [raw-tweets-data]
  (->> raw-tweets-data
      :statuses
      (map raw->tweet)))

(s/fdef search
  :args (s/cat :handle map?
               :query string?)
  :ret (s/coll-of :twitter/tweet))
(defn search
  "Given query returns all matching tweets via Twitter API.
  See https://developer.twitter.com/en/docs/tweets/search/api-reference/get-search-tweets.html"
  [handle query]
  (let [raw-tweets (fetch handle "/search/tweets.json" {:query-params {:q  query}})
        tweets (raw->tweets raw-tweets)]
    tweets))

(comment

  (def twitter-creds (edn/read-string (slurp ".creds.edn")))

  (def my-handle (authenticate twitter-creds))

  (time (search my-handle "clojure"))


;; end of comment
)
