(ns twitter.fetcher.api
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

  Application-only vs Application-user authentication:
  ---------------------------------------------------
  See https://developer.twitter.com/en/docs/basics/authentication/overview/oauth

  The problem with Twitter API is that using the simplified OAuth 2 authentication
  (that is getting an OAuth token by posting to /oauth2/token endpoint)
  the retrieved access token can be used only for read-only access to public information.

  If you want to execute actions on behalf of a user (such as 'posting tweets')
  you need to use 3-ledged-OAuth process: https://developer.twitter.com/en/docs/basics/authentication/overview/3-legged-oauth
  THEREFORE, WE DON'T TRY TO IMPLEMENT IT -> we use https://github.com/chbrown/twttr instead.
  This is done in twitter_api.clj, not this ns which is intended only for _fetching_.


  We use Aleph as http client: https://github.com/ztellman/aleph
  - check https://github.com/dakrone/clj-http for supported request map params.

  See Twitter docs:
  - https://developer.twitter.com/en/docs/basics/getting-started
  - https://developer.twitter.com/en/docs/tweets/search/overview/standard
  - Authentication: https://developer.twitter.com/en/docs/basics/authentication/overview/oauth"
  (:require
   [aleph.http :as http]
   [byte-streams :as bs]
   [clojure.spec.alpha :as s])
  (:import (clojure.lang ExceptionInfo)))

;; this is a map from credentials to oauth tokens
(defonce ^:private credentials-cache (atom {}))

(defn- cached-auth-state
  [creds]
  (get @credentials-cache creds))

(defn- save-auth-state!
  [{:keys [credentials] :as auth-state}]
  (swap! credentials-cache assoc credentials auth-state))

(s/def ::credentials (s/keys :req-un [::api-key ::api-secret]))
(s/def ::auth-state (s/keys ::req-un [::token ::credentials]))
(s/def ::response-data some?)

(def ^:dynamic twitter-api-root-url "https://api.twitter.com")

(defn api-url [relative-url]
  (str twitter-api-root-url "/1.1" relative-url))

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

(defn api-request
  "Fetches or posts data from/to given api resource (`path`)
  by using given OAuth token and adding extra `request-options` which should contain suitable
  request `:method` (most likely `:get` or `:post`).
  For POSTs, the `request-options` should contain the `:form-params` param which will be sent
  as a seralized JSON in the request body.
  This function is supposed to be used internally - prefer high-level fns like `search` and `post-tweet`
  if available."
  [auth-token path request-options]
  (handle-errors
   (fn fetch-fn []
     (-> (http/request (merge
                        {:url (api-url path)
                         :oauth-token auth-token
                         :as :json
                         :content-type :json}
                        request-options))
         deref
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
  See `save-auth-state!`.
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
    (save-auth-state! new-auth-state)
    new-auth-state))

(defn- with-retry-auth
  "Calls given `api-fn` with access token retrieved from credentials cache (authenticating if necessary),
  `path`, and `request-options`.
  Re-authenticates if the call fails to make sure expired access token doesn't break the functionality."
  [creds api-fn path request-options]
  (let [{:keys [token credentials]} (or (cached-auth-state creds) (authenticate! creds))]
    (try
      (api-fn token path request-options)
      (catch ExceptionInfo e
        (println "Got Exception" e)
        (println "=> Reauthenticating")
        (let [{:keys [token]} (authenticate! credentials)]
          (api-fn token path request-options))))))

(authenticate! { :api-key "vloWHzR8piUvGYkmPXRilIz6b", :api-secret "a6OtwU6PAMdAyUhK5vtJBTQoVLOK9P7AAzuH81fxL7Q19Un0v2" })

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
  (let [raw-tweets (with-retry-auth creds api-request "/search/tweets.json"
                     {:method :get :query-params {:q  query}})
        tweets (raw->tweets raw-tweets)]
    tweets))

(comment

  (def twitter-creds (clojure.edn/read-string (slurp ".creds.edn")))

  (time (search twitter-creds "clojure"))


;; end of comment
)
