(ns github.bentomi.kodnevek.ws
  (:require [jsonista.core :as json]
            [clojure.core.async :as async]))

(def ^:private ws-clients (atom {}))

(defn- new-ws-client
  [ws-session send-ch]
  (let [session-id (str (java.util.UUID/randomUUID))
        init-message (json/write-value-as-string [:new-session session-id])]
    (if (async/put! send-ch init-message)
      (swap! ws-clients assoc session-id {:ws-session ws-session
                                          :send-ch send-ch}))))
