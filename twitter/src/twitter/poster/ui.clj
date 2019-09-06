(ns twitter.poster.ui
  "Just a 'playground' namespace supplementing a proper UI.
  Normally, this would be an HTML page caling the server API."
  (:require [clojure.spec.alpha :as s]
            [twitter.poster.app :as app]))

(comment
  (app/schedule-tweet)
  )
