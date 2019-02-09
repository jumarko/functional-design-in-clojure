(defproject twitter "e06-SNAPSHOT"
  :description "Example twitter application discussed in episodes 6 to 11"
  :url "https://clojuredesign.club/"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.9.0"]]
  :main ^:skip-aot twitter.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
