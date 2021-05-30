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
  (let [session-id (str (UUID/randomUUID))
        init-message (write-transit [:new-session session-id])]
    (if (async/put! send-ch init-message)
      (swap! ws-clients assoc session-id {:ws-session ws-session
                                          :send-ch send-ch}))))

(defn send-message [session-id message]
  (if-let [s (get @ws-clients session-id)]
    (async/put! (:send-ch s) (write-transit message))
    (log/info :msg (format "session with ID %s not found" session-id))))

(defn handle-message [event-handler message-text]
  (let [{:keys [session-id message] :as event} (read-transit message-text)]
    (case (get message 0)
      :ping (send-message session-id [:pong (System/currentTimeMillis)])
      (event/handle-event event-handler event))))
