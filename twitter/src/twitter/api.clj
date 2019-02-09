(ns twitter.api
  "Fetches data from twitter.
  We use standard API which returns tweets only for the past 7 days
  and doesn't guarantee completeness.

  We use Aleph as http client: https://github.com/ztellman/aleph

  See Twitter docs:
  - https://developer.twitter.com/en/docs/basics/getting-started
  - https://developer.twitter.com/en/docs/tweets/search/overview/standard"
  (:require
   [aleph.http :as http]
   [byte-streams :as bs]
   [clojure.edn :as edn]))

;; credentials in special local-only file for now
(def twitter-creds (edn/read-string (slurp ".creds.edn")))
(def twitter-api-root-url "https://api.twitter.com")
(def twitter-api-url (str twitter-api-root-url "/1.1"))

(defn api-url [relative-url]
  (str twitter-api-url relative-url))

(defn body->string [response]
  (some-> response :body bs/to-string))

(defn- handle-errors [request-fn]
  (try
    (request-fn)
    (catch clojure.lang.ExceptionInfo e
      (if-let [response-body (some-> e ex-data body->string)]
        (throw (ex-info (ex-message e) (-> e ex-data (assoc :body response-body))))
        (throw e)))))

(defn fetch [handle path]
  (handle-errors
   #(@(http/get (api-url path)
                {:oauth-token (:token handle)}))))

(defn authenticate
  "Authenticates using consumer's api key and secret
  and returns authentication handle (token)."
  [creds]
  (handle-errors
   (fn []
     (let [response-body (:body @(http/post (str twitter-api-root-url "/oauth2/token")
                                            {:basic-auth [(:api-key creds) (:api-secret creds)]
                                             :as :json
                                             :content-type :json
                                             :query-params {:grant_type "client_credentials"}}))]
       (if-let [token (:access_token response-body)]
         {:token token}
         (throw (ex-info "Unexpected response - missing access token" {:body response-body})))))))

(defn search
  "Given query returns all matching tweets via Twitter API"
  [handle query]
  (fetch handle "/search/tweets.json"))

(comment

  (def my-handle (authenticate twitter-creds))

  (search my-handle "clojure")


;; end of comment
)
