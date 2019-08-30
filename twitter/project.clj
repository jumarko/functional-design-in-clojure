(defproject twitter "e06-SNAPSHOT"
  :description "Example twitter application discussed in episodes 6 to 11"
  :url "https://clojuredesign.club/"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [aleph "0.4.6"]
                 [cheshire "5.8.1"]
                 [org.clojure/core.async "0.4.490"]
                 [ring "1.7.1"]
                 [compojure "1.6.1"]
                 [com.stuartsierra/component "0.4.0"]]
  :main ^:skip-aot twitter.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
