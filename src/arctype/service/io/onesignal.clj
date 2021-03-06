(ns ^{:doc "OneSignal push notification API driver"}
  arctype.service.io.onesignal
  (:require
    [clojure.core.async :as async]
    [cheshire.core :as json]
    [clojure.tools.logging :as log]
    [schema.core :as S]
    [sundbry.resource :as resource :refer [with-resources]]
    [arctype.service.util :refer [map-vals xform-validator]]
    [arctype.service.io.oauth :as oauth]
    [arctype.service.io.http.client :as http-client]))

(def Config
  {:api-key S/Str
   :app-id S/Str
   (S/optional-key :endpoint) S/Str
   (S/optional-key :http) http-client/Config})

(def default-config
  {:endpoint "https://onesignal.com/api/v1"
   :http {:throttle
          {:rate 1
           :period :second
           :burst 1}}})

; https://documentation.onesignal.com/reference
(def nil-value "nil")

(defn- api-request
  [{{api-key :api-key
     app-id :app-id
     endpoint :endpoint} :config}
   method 
   params]
  {:url (str endpoint method)
   :method :post
   :headers {"Content-Type" "application/json; charset=utf-8"
             "Authorization" (str "Basic " api-key)}
   :body (json/encode (assoc params :app_id app-id))})

(def xform-response
  (http-client/xform-response
    {:200 (fn [{:keys [body]}] (json/decode body true))
     :201 (fn [{:keys [body]}] (json/decode body true))}))

(defn- response-chan
  []
  (async/chan 1 xform-response))

(def ^:private Filter
  {:field S/Keyword
   :relation S/Keyword
   :value S/Any
   (S/optional-key :key) S/Keyword})

(def ^:private CreateNotificationParams
  ; https://documentation.onesignal.com/reference
  {; The notification's content (excluding the title), a map of language codes to text for each language.
   :contents {:en S/Str} 
   (S/optional-key :filters) (S/maybe [Filter])
   ; The notification's title, a map of language codes to text for each language. 
   (S/optional-key :headings) (S/maybe {:en S/Str})
   ; The notification's subtitle, a map of language codes to text for each language
   (S/optional-key :subtitle) (S/maybe {:en S/Str})
   ; A custom map of data that is passed back to your app.
   (S/optional-key :data) (S/maybe {S/Keyword S/Any})
   ; The URL to open in the browser when a user clicks on the notification.
   (S/optional-key :url) (S/maybe S/Str) 
   ; Time To Live - In seconds. The notification will be expired if the device does not come back online within this time.
   (S/optional-key :ttl) (S/maybe S/Int) 
   ; Only one notification with the same id will be shown on the device. Use the same id to update an existing notification instead of showing a new one.
   (S/optional-key :collapse_id) (S/maybe S/Str)
   ; If blank the small_icon is used. Can be a drawable resource name or a URL.
   ; 256x256
   (S/optional-key :large_icon) (S/maybe S/Str)
   ; Buttons to add to the notification. Icon only works for Android.
   (S/optional-key :buttons) (S/maybe [{:id S/Str 
                                        :text S/Str 
                                        (S/optional-key :icon) S/Str}])

   ;; Android

   ; Sets the devices LED notification light if the device has one. ARGB Hex format. Example(Blue): "FF0000FF"
   (S/optional-key :android_led_color) (S/maybe S/Str) 
   ; Sets the background color of the notification circle to the left of the notification text. ARGB Hex format. Example(Red): "FFFF0000"
   (S/optional-key :android_accent_color) (S/maybe S/Str) 
   ; Sets the lock screen visibility for apps targeting Android API level 21+ running on Android 5.0+ devices.
   ; 1 = Public (default) (Shows the full message on the lock screen unless the user has disabled all notifications from showing on the lock screen. Please consider the user and mark private if the contents are.)
   ; 0 = Private (Hides message contents on lock screen if the user set "Hide sensitive notification content" in the system settings)
   ; -1 = Secret (Notification does not show on the lock screen at all)
   (S/optional-key :android_visibility) (S/maybe (S/enum -1 0 1))
   ; All notifications with the same group will be stacked together using Android's Notification Stacking feature.
   (S/optional-key :android_group) (S/maybe S/Str)
   ; Summary message to display when 2+ notifications are stacked together. Default is "# new messages". Include $[notif_count] in your message and it will be replaced with the current number.
   ; Example: {"en": "You have $[notif_count] new messages"}
   (S/optional-key :android_group_message) (S/maybe {:en S/Str})
   ; Sound file that is included in your app to play instead of the default device notification sound. NOTE: Leave off file extension for Android.
   ; Example:  "notification"
   (S/optional-key :android_sound) (S/maybe S/Str)

   ;; iOS

   ; Describes whether to set or increase/decrease your app's iOS badge count by the ios_badgeCount specified count.
   (S/optional-key :ios_badgeType) (S/maybe (S/enum "None" "SetTo" "Increase"))
   ; Used with ios_badgeType, describes the value to set or amount to increase/decrease your app's iOS badge count by.
   (S/optional-key :ios_badgeCount) (S/maybe S/Int)

   ; Sound file that is included in your app to play instead of the default device notification sound. Pass  "nil" to disable vibration and sound for the notification.
   ; Example: "notification.wav"
   (S/optional-key :ios_sound) (S/maybe S/Str)})

(S/defn tag-filter :- Filter
  [key-name key-value]
  {:field :tag
   :key key-name
   :relation :=
   :value key-value})

(S/defn create-notification
  "Request a notification to be sent."
  [this 
   params :- CreateNotificationParams]
  (with-resources this [:http]
    (let [req (api-request this "/notifications" params)]
      (http-client/request! http req (response-chan)))))

(defrecord OneSignalClient [config])

(S/defn create
  [resource-name
   config :- Config]
  (let [config (merge default-config config)]
    (resource/make-resource
      (map->OneSignalClient
        {:config config})
      resource-name nil
      [(http-client/create :http (:http config))])))
