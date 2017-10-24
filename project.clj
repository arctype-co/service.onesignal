(defproject co.arctype/service.onesignal "0.1.0-SNAPSHOT"
  :dependencies 
  [[org.clojure/clojure "1.8.0"]
   [arctype/service "0.1.0-SNAPSHOT"]
   [clj-http "3.7.0"]
   [oauth-clj "0.1.15"
    :exclusions [clj-http]]])
