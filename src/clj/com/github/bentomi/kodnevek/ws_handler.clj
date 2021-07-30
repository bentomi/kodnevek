(ns com.github.bentomi.kodnevek.ws-handler
  "WebSocket handler. Keeps track of the clients and routes messages."
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as spec]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [cognitect.transit :as transit]
            [com.github.bentomi.kodnevek.event :as event]
            [com.github.bentomi.kodnevek.ws :as ws])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream)
           (java.nio.charset StandardCharsets)
           (java.util UUID)))

(defprotocol Closed?
  (closed? [this] "Retrurns true iff `this` is closed."))

(extend-type org.eclipse.jetty.websocket.common.WebSocketSession
  Closed?
  (closed? [this] (not (.isOpen this))))

(spec/fdef write-transit
  :args (spec/cat :message any?)
  :ret string?)

(defn- write-transit
  "Returns the transit serialized version of `message`."
  [message]
  (let [out (ByteArrayOutputStream. 256)
        writer (transit/writer out :json)]
    (transit/write writer message)
    (.toString out StandardCharsets/UTF_8)))

(spec/fdef read-transit
  :args (spec/cat :message-text string?)
  :ret any?)

(defn- read-transit
  "Returns the transit de-serialized version of the string `message-text`."
  [^String message-text]
  (let [message-bytes (.getBytes message-text StandardCharsets/UTF_8)
        in (ByteArrayInputStream. message-bytes)
        reader (transit/reader in :json)]
    (transit/read reader)))

(spec/def ::ws-session (partial satisfies? Closed?))
(spec/def ::send-ch any?)
(spec/def ::client-id uuid?)
(spec/def ::session-token string?)
(spec/def ::session (spec/keys :req-un [::ws-session ::send-ch]))
(spec/def ::clients (spec/every-kv ::client-id ::session))
(spec/def ::tokens (spec/every-kv ::session-token ::session))
(spec/def ::ws-clients (spec/keys :opt-un [::clients ::tokens]))

(spec/fdef init-session
  :args (spec/cat :session-token ::session-token
                  :ws-session ::ws-session
                  :send-ch ::send-ch)
  :ret (spec/nilable ::session))

(defn- init-session [session-token ws-session send-ch]
  (let [init-message (write-transit [:new-session session-token])]
    (when (async/put! send-ch init-message)
      {:ws-session ws-session, :send-ch send-ch})))

(spec/fdef register-session
  :args (spec/cat :ws-clients ::ws-clients
                  :client-id ::client-id
                  :session-token ::session-token)
  :ret (spec/nilable ::ws-clients))

(defn- register-session [ws-clients client-id session-token]
  (if-let [session (get-in ws-clients [:tokens session-token])]
    (-> ws-clients
        (update :clients assoc client-id session)
        (update :tokens dissoc session-token))
    ws-clients))

(spec/fdef send-message*
  :args (spec/cat :ws-clients ::ws-clients
                  :client-id ::client-id
                  :message any?)
  :ret (spec/nilable boolean?))

(defn- send-message* [ws-clients client-id message]
  (if-let [send-ch (get-in ws-clients [:clients client-id :send-ch])]
    (async/put! send-ch (write-transit message))
    (log/warnf "session for client %s not found" client-id)))

(defn- keys-of-matching
  "Returns a lazy sequence of the keys from the map `m` for which `pred`
  is truthy when invoked on the corresponding entry."
  [m pred]
  (sequence (comp (filter pred) (map key)) m))

(defn- dissoc-subkeys
  "Dissociates the keys `subkeys` from the map under key `k` in map `m`."
  [m k subkeys]
  (apply update m k dissoc subkeys))

(def ^:private gc-period
  (.toMillis java.util.concurrent.TimeUnit/HOURS 1))

(defn- gc
  ([ws-clients]
   (gc ws-clients (System/currentTimeMillis)))
  ([ws-clients now]
   (let [timeout (- now gc-period)]
     (when (< (-> @ws-clients meta :last-gc) timeout)
       (let [closed-sessions
             (seq (keys-of-matching (:clients @ws-clients)
                                    #(-> % val :ws-session closed?)))
             stale-tokens
             (seq (keys-of-matching (:tokens @ws-clients)
                                    #(< (-> % val meta :created) timeout)))]
         (swap! ws-clients
                #(cond-> (vary-meta % assoc :last-gc now)
                   closed-sessions (dissoc-subkeys :clients closed-sessions)
                   stale-tokens (dissoc-subkeys :tokens stale-tokens))))))))

(deftype SimpleWSHandler [ws-clients]
  ws/WSHandler
  (new-ws-client [_this ws-session send-ch]
    (gc ws-clients)
    (let [session-token (str (UUID/randomUUID))
          now (System/currentTimeMillis)]
      (when-let [session (init-session session-token ws-session send-ch)]
        (swap! ws-clients
               update :tokens
               assoc session-token (with-meta session {:created now})))))
  (handle-message [_this event-handler message-text]
    (gc ws-clients)
    (let [{:keys [client-id message] :as event} (read-transit message-text)]
    (case (get message 0)
      :register-session
      (swap! ws-clients register-session client-id (get message 1))
      :ping
      (send-message* @ws-clients client-id [:pong (System/currentTimeMillis)])
      (event/handle-event event-handler event))))
  (handle-close [_this num-code reason-text]
    (gc ws-clients)
    (log/infof "WS closed (%s) - %s" num-code reason-text))
  ws/MessageSender
  (send-message [_this client-id message]
    (send-message* @ws-clients client-id message)))

(defn ->ws-handler []
  (SimpleWSHandler. (atom ^{:last-gc (System/currentTimeMillis)} {})))

(defmethod ig/init-key ::provider [_key _config]
  (->ws-handler))
