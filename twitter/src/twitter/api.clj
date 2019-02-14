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
   [clojure.spec.alpha :as s])
  (:import (clojure.lang ExceptionInfo)))


(s/def ::credentials (s/keys :req-un [::api-key ::api-secret]))
(s/def ::auth-state (s/keys ::req-un [::token ::credentials]))
(s/def ::response-data some?)

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

(defn handle-errors [request-fn]
  (try
    (request-fn)
    (catch ExceptionInfo e
      (if-let [body-text (some-> e ex-data response-body body->string)]
        (throw (ex-info (ex-message e) (-> e ex-data (assoc :body body-text))))
        (throw e)))))

(defn fetch
  "Fetches data from given api resource (`path`)
  by using given authentication state and adding extra `request-options`.
  This is supposed to be used internally - prefer `fetch-retry-auth` over this function."
  [auth-state path request-options]
  (handle-errors
   (fn fetch-fn [] 
     (-> (http/get (api-url path)
                   (merge 
                    {:oauth-token (:token auth-state)
                     :as :json}
                    request-options)) deref
         response-body))))

(s/fdef authenticate
  :args (s/cat :credentials ::credentials)
  :ret ::auth-state)
(defn authenticate
  "Authenticates using consumer's api key and secret
  and returns authentication auth-state (token).
  See https://developer.twitter.com/en/docs/basics/authentication/api-reference/token.html"
  [credentials]
  (handle-errors
   (fn auth-fn []
     (let [response-body (response-body @(http/post (str twitter-api-root-url "/oauth2/token")
                                            {:basic-auth [(:api-key credentials) (:api-secret credentials)]
                                             :as :json
                                             :content-type :json
                                             :query-params {:grant_type "client_credentials"}}))]
       (if-let [token (:access_token response-body)]
         {:token token :credentialss credentials}
         (throw (ex-info "Unexpected response - missing access token" {:body response-body})))))))

(s/fdef fetch-retry-auth
  :args (s/cat :auth-state ::auth-state
               :path string?
               :request-options map?)
  :ret (s/tuple ::auth-state ::response-data))
(defn fetch-retry-auth
  "Similar to `fetch` but retries on authentication errors.
  Gives up after a single retry since that's very likely another issue with the call
  or permenant authentication error."
  ;; I guess it wasn't mentioned in the podcast but `:credentials` are necessary
  ;; to be able to re-authenticate at any point
  [{:keys [credentials] :as auth-state} path request-options]
  (try
    [auth-state (fetch auth-state path request-options)]
    (catch ExceptionInfo e
      (println "Got Exception" e)
      (println "=> Reauthenticating")
      (let [new-auth-state (authenticate credentials)]
        [new-auth-state (fetch new-auth-state path request-options)]))))

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
  :ret (s/tuple map? (s/coll-of :twitter/tweet)))
(defn search
  "Finds all matching tweets for given query.
  Returns a tuple [updated-auth-state tweets].
  See https://developer.twitter.com/en/docs/tweets/search/api-reference/get-search-tweets.html"
  [auth-state query]
  (let [[updated-auth-state raw-tweets] (fetch-retry-auth auth-state "/search/tweets.json" {:query-params {:q  query}})
        tweets (raw->tweets raw-tweets)]
    [updated-auth-state tweets]))

(comment

  (def twitter-creds (edn/read-string (slurp ".creds.edn")))

  (def my-auth-state (authenticate twitter-creds))

  (time (search my-auth-state "clojure"))


;; end of comment
)
