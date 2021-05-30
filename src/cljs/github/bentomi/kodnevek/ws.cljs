(ns github.bentomi.kodnevek.ws
  (:require [cognitect.transit :as transit]
            [re-frame.core :as re-frame]))

(defonce ^:private client-id (random-uuid))

(def ^:private transit-reader (transit/reader :json))
(def ^:private transit-writer (transit/writer :json))

(defn- parse-message [message-text]
  (transit/read transit-reader message-text))

(defn- write-message [message]
  (transit/write transit-writer message))

(re-frame/reg-fx
 :ws-send
 (fn [{:keys [ws message]}]
   (.send ws (write-message message))))

(re-frame/reg-event-db
 ::close-socket
 (fn [db _]
   (dissoc db ::socket)))

(re-frame/reg-event-db
 ::set-socket
 (fn [db [_ ws]]
   (assoc db ::socket {:ws ws})))

(re-frame/reg-sub
 ::socket
 (fn [db]
   (::socket db)))

(re-frame/reg-event-db
 ::set-session
 (fn [db [_ session-id]]
   (assoc-in db [::socket :session-id] session-id)))

(re-frame/reg-event-fx
 ::send-message
 (fn [{db :db} [_ message error-event]]
   (let [{:keys [ws session-id]} (::socket db)]
     (if (nil? session-id)
       (when (some? error-event)
         {:dispatch error-event})
       {:ws-send {:ws ws
                  :message {:session-id session-id
                            :message message}}}))))

(defn- handle-new-session [session-id]
  (re-frame/dispatch [::set-session session-id])
  (re-frame/dispatch [::send-message [:client-id client-id]]))

(defn- handle-message-event [handler event]
  (let [message (-> event .-data parse-message)]
    (case (get message 0)
      :new-session (handle-new-session (get message 1))
      :pong (js/console.debug "pong" (get message 1))
      (handler message))))

(defn- ping []
  (re-frame/dispatch [::send-message [:ping (js/Date.now)]]))

(def ^:private ping-interval-ms 50000)

(defonce ^:private ping-timer-id (js/setInterval ping ping-interval-ms))

(defn connect [url handler]
  (when-not @(re-frame/subscribe [::socket])
    (let [message-handler (partial handle-message-event handler)
          w (js/WebSocket. url)]
      (set! (.-onmessage w) (partial handle-message-event handler))
      (set! (.-onerror w) #(js/console.error %))
      (set! (.-onclose w) #(do (re-frame/dispatch-sync [::close-socket])
                               (connect url handler)))
      (re-frame/dispatch [::set-socket w]))))
