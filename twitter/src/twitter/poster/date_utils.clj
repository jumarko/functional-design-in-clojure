(ns twitter.poster.date-utils)

(defn sql-timestamp->zoned-date-time
  "Converts `java.sql.Timestamp` to `java.time.ZonedDateTime` with UTC timezone."
  [sql-timestamp]
  (-> sql-timestamp .toInstant (.atZone java.time.ZoneOffset/UTC)))

(defn zoned-date-time->date
  "Converts `java.time.ZonedDateTime` to `java.util.Date`."
  [zoned-date-time]
  (-> zoned-date-time .toInstant java.util.Date/from))
