(ns github.bentomi.kodnevek.event)

(defprotocol EventHandler
  (handle-event [this event]))
