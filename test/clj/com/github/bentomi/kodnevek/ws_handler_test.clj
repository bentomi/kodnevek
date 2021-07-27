(ns com.github.bentomi.kodnevek.ws-handler-test
  (:require [clojure.test :as test :refer [deftest is]]
            [clojure.core.async :as async]
            [clojure.spec.alpha :as spec]
            [clojure.spec.test.alpha :as stest]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [com.github.bentomi.kodnevek.event :as event]
            [com.github.bentomi.kodnevek.ws :as ws]
            [com.github.bentomi.kodnevek.ws-handler :as h]))

(stest/instrument)

(defn- random-uuid []
  (java.util.UUID/randomUUID))

(def ^:private rm @#'h/read-transit)

(defn- wm [client-id message]
  (#'h/write-transit {:client-id client-id, :message message}))

(deftest smoke-test
  (let [start (System/currentTimeMillis)
        client-id (random-uuid)
        h (h/->ws-handler)
        send-ch (async/chan)
        session-closed (atom false)
        ws-session (reify h/Closed? (closed? [this] @session-closed))
        dangling-session (reify h/Closed? (closed? [this] false))]
    (is (empty? (-> h .ws_clients deref :tokens)))
    (is (empty? (-> h .ws_clients deref :clients)))
    (ws/new-ws-client h ws-session send-ch)
    (ws/new-ws-client h dangling-session (async/chan 1)) ; will never register
    (is (= 2 (count (-> h .ws_clients deref :tokens))))
    (is (empty? (-> h .ws_clients deref :clients)))
    ;; Finish the handshake: read init message and register with client-id.
    (let [init-message-transit (async/<!! send-ch)
          [message-type token] (rm init-message-transit)]
      (is (= :new-session message-type))
      (is (string? token))
      (ws/handle-message h nil (wm client-id [:register-session token]))
      ;; Duplicate registration is OK.
      (ws/handle-message h nil (wm client-id [:register-session token])))
    ;; Registering with unknown token is noop.
    (ws/handle-message h nil (wm (random-uuid)
                                 [:register-session (str (random-uuid))]))
    (is (= 1 (count (-> h .ws_clients deref :tokens))))
    (is (seq (-> h .ws_clients deref :clients)))
    ;; Get :pong after sending :ping.
    (ws/handle-message h nil (wm client-id [:ping 0]))
    (let [pong-transit (async/<!! send-ch)
          [message-type time] (rm pong-transit)]
      (is (= :pong message-type))
      (is (int? time)))
    (let [test-message (random-uuid)]
      ;; Application messages are forwarded to the handler.
      (ws/handle-message
       h
       (reify event/EventHandler
         (handle-event [_ event]
           (is (= {:client-id client-id, :message test-message} event))))
       (wm client-id test-message))
      ;; Messages from the system are forwarded to the client.
      (ws/send-message h client-id test-message)
      (is (= test-message (rm (async/<!! send-ch)))))
    ;; Closed sessions and dangling tokens are garbage collected after a while.
    (reset! session-closed true)
    (ws/handle-close h 0 "close test")
    (#'h/gc (.ws_clients h) start)
    (is (seq (-> h .ws_clients deref :tokens)))
    (#'h/gc (.ws_clients h) (+ start @#'h/gc-period 60000))
    (is (empty? (-> h .ws_clients deref :tokens)))
    (is (empty? (-> h .ws_clients deref :clients)))
    (ws/send-message h (random-uuid) "ignored")))
