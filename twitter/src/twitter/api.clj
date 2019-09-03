(ns twitter.api
  "Fetches data from twitter.
  We use standard API which returns tweets only for the past 7 days
  and doesn't guarantee completeness.

  Authentication:
  --------------
  Originally, this was implemented as a tuple [updated-auth-state query-result]
  returned from the `search` function to the client.
  However, we now maintain a cache of OAuth tokens in `credentials-cache`
  and thus the client only needs to pass credentials (api key).
  This simplified the API and clients don't need to maintain authentication state at all.

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

;; this is a map from credentials to oauth tokens
(defonce ^:private credentials-cache (atom {}))

(defn- cached-auth-state [creds]
  (get @credentials-cache creds))

(defn- save-auth-state [{:keys [credentials] :as auth-state}]
  (swap! credentials-cache assoc credentials auth-state))

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
  by using given OAuth token and adding extra `request-options`.
  This is supposed to be used internally - prefer `fetch-retry-auth` over this function."
  [auth-token path request-options]
  (handle-errors
   (fn fetch-fn [] 
     (-> (http/get (api-url path)
                   (merge 
                    {:oauth-token auth-token
                     :as :json}
                    request-options)) deref
         response-body))))

;; TODO: this was an attempt to make a "constructor" but we don't use any matching "selectors"
;; just :keys destructuring everywhere...
(defn- make-auth-state [token creds]
  {:token token :credentials creds})

(s/fdef authenticate!
  :args (s/cat :credentials ::credentials)
  :ret ::auth-state)
(defn- authenticate!
  "Authenticates using consumer's api key and secret
  and returns authentication auth-state containing OAuth token.
  Saves new auth-state into authentication cache to be reused and re-authenticated only when necessary.
  See `save-auth-state`.
  See https://developer.twitter.com/en/docs/basics/authentication/api-reference/token.html"
  [credentials]
  (let [new-auth-state
        (handle-errors
         (fn auth-fn []
           (let [response-body (response-body @(http/post (str twitter-api-root-url "/oauth2/token")
                                                          {:basic-auth [(:api-key credentials) (:api-secret credentials)]
                                                           :as :json
                                                           :content-type :json
                                                           :query-params {:grant_type "client_credentials"}}))]
             (if-let [token (:access_token response-body)]
               (make-auth-state token credentials)
               (throw (ex-info "Unexpected response - missing access token" {:body response-body}))))))]
    (save-auth-state new-auth-state)))

(s/fdef fetch-retry-auth
  :args (s/cat :auth-state ::auth-state
               :path string?
               :request-options map?)
  :ret ::response-data)
(defn fetch-retry-auth
  "Similar to `fetch` but retries on authentication errors.
  Gives up after a single retry since that's very likely another issue with the call
  or permenant authentication error."
  ;; I guess it wasn't mentioned in the podcast but `:credentials` are necessary
  ;; to be able to re-authenticate at any point
  [creds path request-options]
  (let [{:keys [token credentials]} (or (cached-auth-state creds) (authenticate! creds))]
    (try
      (fetch token path request-options)
      (catch ExceptionInfo e
        (println "Got Exception" e)
        (println "=> Reauthenticating")
        (let [{:keys [token]} (authenticate! credentials)]
          (fetch token path request-options))))))

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
  :args (s/cat :creds ::credentials
               :query string?)
  :ret (s/coll-of :twitter/tweet))
(defn search
  "Finds all matching tweets for given query and returns them.
  Reauthenticates automatically if an OAuth token is expired.
  See https://developer.twitter.com/en/docs/tweets/search/api-reference/get-search-tweets.html"
  [creds query]
  (let [raw-tweets (fetch-retry-auth creds "/search/tweets.json" {:query-params {:q  query}})
        tweets (raw->tweets raw-tweets)]
    tweets))

(comment

  (def twitter-creds (edn/read-string (slurp ".creds.edn")))

  (def my-auth-state (authenticate! twitter-creds))

  (time (search my-auth-state "clojure"))


;; end of comment
)
