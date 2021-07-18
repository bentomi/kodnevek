(ns com.github.bentomi.kodnevek.ws-handler
  "WebSocket handler. Keeps track of the clients and routes messages."
  (:require [cognitect.transit :as transit]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async]
            [integrant.core :as ig]
            [com.github.bentomi.kodnevek.event :as event]
            [com.github.bentomi.kodnevek.ws :as ws])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream)
           (java.nio.charset StandardCharsets)
           (java.util UUID)))

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

(defn- register-session [ws-clients client-id session-token]
  (if-let [session (get-in ws-clients [:tokens session-token])]
    (-> ws-clients
        (update :clients assoc client-id session)
        (update :tokens dissoc session-token))
    ws-clients))

(defn- new-ws-client* [ws-clients ws-session send-ch]
  (let [session-token (str (UUID/randomUUID))
        init-message (write-transit [:new-session session-token])]
    (if (async/put! send-ch init-message)
      (let [session {:ws-session ws-session, :send-ch send-ch}]
        (swap! ws-clients update :tokens assoc session-token session)))))

(defn- send-message* [ws-clients client-id message]
  (if-let [send-ch (get-in @ws-clients [:clients client-id :send-ch])]
    (async/put! send-ch (write-transit message))
    (log/warnf "session for client %s not found" client-id)))

(defn- handle-message* [ws-clients event-handler message-text]
  (let [{:keys [client-id message] :as event} (read-transit message-text)]
    (case (get message 0)
      :register-session
      (swap! ws-clients register-session client-id (get message 1))
      :ping
      (send-message* ws-clients client-id [:pong (System/currentTimeMillis)])
      (event/handle-event event-handler event))))

(deftype SimpleWSHandler [ws-clients]
  ws/WSHandler
  (new-ws-client [this ws-session send-ch]
    (new-ws-client* ws-clients ws-session send-ch))
  (handle-message [this event-handler message-text]
    (handle-message* ws-clients event-handler message-text))
  ws/MessageSender
  (send-message [this client-id message]
    (send-message* ws-clients client-id message)))

(defmethod ig/init-key ::provider [_key config]
  (SimpleWSHandler. (atom {})))
