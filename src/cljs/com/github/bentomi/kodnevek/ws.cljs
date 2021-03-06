(ns com.github.bentomi.kodnevek.ws
  "WebSocket handling."
  (:require [cognitect.transit :as transit]
            [re-frame.core :as re-frame]
            [com.github.bentomi.kodnevek.errors :as errors]))

(defonce ^:private client-id (random-uuid))

#_{:clj-kondo/ignore [:invalid-arity]}
(def ^:private transit-reader (transit/reader :json))
#_{:clj-kondo/ignore [:invalid-arity]}
(def ^:private transit-writer (transit/writer :json))

#_{:clj-kondo/ignore [:invalid-arity]}
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
   (assoc db ::socket ws)))

(re-frame/reg-sub
 ::socket
 (fn [db]
   (::socket db)))

(re-frame/reg-event-fx
 ::send-message
 (fn [{db :db} [_ message error-event]]
   (if-let [ws (::socket db)]
     {:ws-send {:ws ws
                :message {:client-id client-id
                          :message message}}}
     (when (some? error-event)
         {:dispatch error-event}))))

(defn send-message-event [message]
  [::send-message message [::errors/no-session-error (get message 0)]])

(defn- handle-new-session [handler session-token]
  (re-frame/dispatch [::send-message [:register-session session-token]])
  (handler [:new-session]))

(defn- handle-message-event [handler event]
  (let [message (-> event .-data parse-message)]
    (case (get message 0)
      :new-session (handle-new-session handler (get message 1))
      :pong (js/console.debug "pong" (get message 1))
      (handler message))))

(defn- ping []
  (re-frame/dispatch [::send-message [:ping (js/Date.now)]]))

(def ^:private ping-interval-ms 50000)

#_{:clj-kondo/ignore [:unused-private-var]}
(defonce ^:private ping-timer-id (js/setInterval ping ping-interval-ms))

(defn connect [url handler]
  (when-not @(re-frame/subscribe [::socket])
    (let [w (js/WebSocket. url)]
      (set! (.-onmessage w) (partial handle-message-event handler))
      (set! (.-onerror w) #(js/console.error %))
      (set! (.-onclose w) #(do (re-frame/dispatch-sync [::close-socket])
                               (connect url handler)))
      (re-frame/dispatch [::set-socket w]))))
