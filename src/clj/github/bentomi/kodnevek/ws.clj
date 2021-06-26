(ns github.bentomi.kodnevek.ws
  (:require [cognitect.transit :as transit]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async]
            [github.bentomi.kodnevek.event :as event])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream)
           (java.nio.charset StandardCharsets)
           (java.util UUID)))

(def ^:private ws-clients (atom {}))

(defn- write-transit [message]
  (let [out (ByteArrayOutputStream. 256)
        writer (transit/writer out :json)]
    (transit/write writer message)
    (.toString out)))

(defn- read-transit [message-text]
  (let [message-bytes (.getBytes message-text StandardCharsets/UTF_8)
        in (ByteArrayInputStream. message-bytes)
        reader (transit/reader in :json)]
    (transit/read reader)))

(defn new-ws-client
  [ws-session send-ch]
  (let [session-token (str (UUID/randomUUID))
        init-message (write-transit [:new-session session-token])]
    (if (async/put! send-ch init-message)
      (let [session {:ws-session ws-session, :send-ch send-ch}]
        (swap! ws-clients update :tokens assoc session-token session)))))

(defn- register-session
  ([client-id session-token]
   (swap! ws-clients register-session client-id session-token))
  ([ws-clients client-id session-token]
   (if-let [session (get-in ws-clients [:tokens session-token])]
     (-> ws-clients
         (update :clients assoc client-id session)
         (update :tokens dissoc session-token))
     ws-clients)))

(defn send-message [client-id message]
  (if-let [send-ch (get-in @ws-clients [:clients client-id :send-ch])]
    (async/put! send-ch (write-transit message))
    (log/warnf "session for client %s not found" client-id)))

(defn handle-message [event-handler message-text]
  (let [{:keys [client-id message] :as event} (read-transit message-text)]
    (case (get message 0)
      :register-session (register-session client-id (get message 1))
      :ping (send-message client-id [:pong (System/currentTimeMillis)])
      (event/handle-event event-handler event))))
