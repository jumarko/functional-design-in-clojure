(defproject twitter "e21-27-SNAPSHOT"
  :description "Example twitter application discussed in episodes 6 to 11. Also Twitter Poster (ep. 21-27)"
  :url "https://clojuredesign.club/"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [aleph "0.4.6"]
                 [cheshire "5.8.1"]
                 [org.clojure/core.async "0.4.500"]
                 [ring "1.7.1"]
                 [compojure "1.6.1"]
                 [com.stuartsierra/component "0.4.0"]
                 ;; See https://github.com/seancorfield/usermanager-example/blob/master/src/usermanager/model/user_manager.clj
                 [seancorfield/next.jdbc "1.0.6"]
                 [ring/ring-json "0.5.0"]
                 [com.h2database/h2 "1.4.199"]
                 ;; http://brownsofa.org/blog/2015/06/14/clojure-in-production-logging/
                 ;; https://github.com/clojure/tools.logging
                 ;; https://logging.apache.org/log4j/2.x/manual/configuration.html
                 [org.clojure/tools.logging "0.5.0"]
                 ;; Note: this is really needed https://logging.apache.org/log4j/2.x/maven-artifacts.html
                 ;; Otherwise you'd get "ERROR StatusLogger No Log4j 2 configuration file found. " 
                 [org.apache.logging.log4j/log4j-api "2.12.1"]
                 [org.apache.logging.log4j/log4j-core"2.12.1"]
                 ;; visualize components of your system - handy for arch. diagrams?
                 [walmartlabs/system-viz "0.4.0"]

                 ;; external library for talking to twitter
                 ;; so we don't need to implement complex authentication worfklow ourselves
                 [twttr "3.2.2"]
                 ]
  :main ^:skip-aot twitter.poster.app
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:injections   [(require '[orchestra.spec.test :as stest])
                                  (stest/instrument)
                                  (.println System/err "Instrumented specs")]
                   :dependencies [[orchestra "2019.02.06-1"]]}})

