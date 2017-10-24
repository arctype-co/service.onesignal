(ns ^{:doc "Because the other clojure OAuth clients suck"}
  arctype.service.io.oauth
  (:require
    [clj-http.client :as clj-http-client]
    [oauth.v1 :as v1]))

(defn- oauth-defaults
  []
  {:oauth-signature-method "HMAC-SHA1"
   :oauth-timestamp (str (int (/ (System/currentTimeMillis) 1000)))
   :oauth-nonce (v1/oauth-nonce)
   :oauth-version "1.0"})

(defn authorize-0
  "Authorization for a 0-legged request"
  [consumer-key consumer-secret req]
  (-> req
      (merge (clj-http-client/parse-url (:url req))
             (oauth-defaults)
             {:oauth-consumer-key consumer-key
              :oauth-consumer-secret consumer-secret})
      (v1/oauth-sign-request consumer-secret)
      v1/oauth-authorization-header))

