(ns github.bentomi.kodnevek.game
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [github.bentomi.kodnevek.event :as event]
            [github.bentomi.kodnevek.words :as words]
            [github.bentomi.kodnevek.ws :as ws]))

(def ^:private size 25)
(def ^:private reds 9)
(def ^:private blues 8)
(def ^:private whites 7)
(def ^:private blacks (- size reds blues whites))

(defn- generate-colours []
  (->> (concat (repeat reds :red) (repeat blues :blue)
               (repeat whites :white) (repeat blacks :black))
       shuffle))

(defonce client-sessions (atom {}))

(defn- register-client [client-id session-id]
  (swap! client-sessions assoc client-id session-id))

(defprotocol GameManager
  (create-game [this lang]
    "Create a new game."))

(deftype SimpleGameManager [word-provider]
  GameManager
  (create-game [this lang]
    (map (fn [word colour] {:word word, :colour colour})
         (words/get-words word-provider lang size)
         (generate-colours)))
  event/EventHandler
  (handle-event [this {:keys [session-id message] :as event}]
    (case (get message 0)
      :create-game (let [lang (get message 1)
                         resp [:new-game (create-game this lang)]]
                     (ws/send-message session-id resp))
      :client-id (register-client (get message 1) session-id)
      (log/info "received event" event))))

(defmethod ig/init-key ::provider [_key config]
  (SimpleGameManager. (::words/provider config)))
