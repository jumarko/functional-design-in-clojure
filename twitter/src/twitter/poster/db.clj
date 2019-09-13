(ns twitter.poster.db
  "A low-level database component providing db connection for other components.
  Check https://github.com/seancorfield/usermanager-example/blob/master/src/usermanager/model/user_manager.clj"
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]))


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
  (let [auto-key (if (= "sqlite" db-type)
                   "primary key autoincrement"
                   (str "generated always as identity"
                        " (start with 1, increment by 1)"
                        " primary key"))]
    (try
      (jdbc/execute-one! (db)
                         [(str "
create table department (
  id            integer " auto-key ",
  name          varchar(32)
)")])
      (jdbc/execute-one! (db)
                         [(str "
create table addressbook (
  id            integer " auto-key ",
  first_name    varchar(32),
  last_name     varchar(32),
  email         varchar(64),
  department_id integer not null
)")])
      (println "Created database and addressbook table!")
      ;; if table creation was successful, it didn't exist before
      ;; so populate it...
      (try
        ;; TODO populate some data? (probably not needed)
        ;; (doseq [d departments]
        ;;   (sql/insert! (db) :department {:name d}))
        ;; (doseq [row initial-user-data]
        ;;   (sql/insert! (db) :addressbook row))
        (println "Populated database with initial data!")
        (catch Exception e
          (println "Exception:" (ex-message e))
          (println "Unable to populate the initial data -- proceed with caution!")))
      (catch Exception e
        (println "Exception:" (ex-message e))
        (println "Looks like the database is already setup?")))))

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
        #_(populate database (:dbtype db-spec))
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

