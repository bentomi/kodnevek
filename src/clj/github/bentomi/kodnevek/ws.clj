(ns github.bentomi.kodnevek.ws
  "WebSocket handler interface.")

(defprotocol MessageSender
  (send-message [this client-id message]
    "Send `message` to the client with ID `client-id`."))

(defprotocol WSHandler
  (new-ws-client [this ws-session send-ch]
    "Register the new session `ws-session` with the output channel `send-ch`.")
  (handle-message [this event-handler message-text]
    "Handle the incoming message serialized in `message-text` passing
  application level messages to `event-handler`."))
