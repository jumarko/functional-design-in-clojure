(ns twitter.poster.db
  "A low-level database component providing db connection for other components.
  Check https://github.com/seancorfield/usermanager-example/blob/master/src/usermanager/model/user_manager.clj"
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [clojure.tools.logging :as log]))


;;; database initialization

;; our database connection and initial data
;;
(def ^:private my-db {:dbtype "h2" :dbname "twitter_poster_h2_db"})

;; TODO these are just examples
;; (def ^:private departments
;;   "List of departments."
;;   ["Accounting" "Sales" "Support" "Development"])

;; (def ^:private initial-user-data
;;   "Seed the database with this data."
;;   [{:first_name "Sean" :last_name "Corfield"
;;     :email "sean@worldsingles.com" :department_id 4}])

(defn- populate
  "Called at application startup. Attempts to create the
  database table and populate it. Takes no action if the
  database table already exists."
  [db db-type]
  (try
    (jdbc/execute-one! (db)
                       ;; notice that TIMESTAMP WITH TIME ZONE actually stores at least the TZ offset unlike postgresql
                       [(str "
create table tweets (
  id identity not null primary key,
  tweet_id varchar(128),
  text varchar(256),
  post_at timestamp with time zone,
  posted_at timestamp
)")])
    (log/info "Created database and tweets table!")
    ;; if table creation was successful, it didn't exist before (we could populate it with some data now)
    (catch Exception e
      (log/info "Exception:" (ex-message e))
      (log/info "Looks like the database is already setup?"))))

;; See https://github.com/seancorfield/usermanager-example/blob/master/src/usermanager/model/user_manager.clj#L72
(defrecord Database [db-spec     ; configuration
                     datasource] ; state
  component/Lifecycle
  (start [this]
    (if datasource
      this ; already initialized
      (let [database (assoc this :datasource (jdbc/get-datasource db-spec))]
        ;; set up database if necessary
        ;; TODO: don't do this yet (migratus would be better)
        (populate database (:dbtype db-spec))
        database)))
  (stop [this]
    (assoc this :datasource nil))

  ;; allow the Database component to be "called" with no arguments
  ;; to produce the underlying datasource object
  clojure.lang.IFn
  (invoke [this] datasource))

;; Note: this is called `setup-database` in Sean's example:
;; https://github.com/seancorfield/usermanager-example/blob/master/src/usermanager/model/user_manager.clj#L91
(defn make-database
  ([] (make-database {:db-spec my-db}))
  ([db-spec]
   (map->Database db-spec)))

