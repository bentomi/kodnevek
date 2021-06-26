(ns github.bentomi.kodnevek.game
  "Game handling logic for the backend."
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [github.bentomi.kodnevek.board :as board]
            [github.bentomi.kodnevek.event :as event]
            [github.bentomi.kodnevek.game-store :as game-store]
            [github.bentomi.kodnevek.words :as words]
            [github.bentomi.kodnevek.ws :as ws]
            [github.bentomi.kodnevek.util :refer [add-to-set]]))

(def ^:private size 25)
(def ^:private first-team-size 9)
(def ^:private second-team-size 8)
(def ^:private whites 7)
(def ^:private blacks (- size first-team-size second-team-size whites))

(def ^:private opponent {:red :blue, :blue :red})

(defn- generate-colours [first-colour]
  (shuffle (concat (repeat first-team-size first-colour)
                   (repeat second-team-size (opponent first-colour))
                   (repeat whites :white)
                   (repeat blacks :black))))

(defprotocol GameManager
  (create-game [this client-id lang first-colour]
    "Create a new game for client with ID `client-id` in language `lang` with
  the team `first-colour` making the first guess.")
  (open-game [this client-id game-id]
    "Open an existing game with ID `game-id` for client with ID `client-id`
  as administrator.")
  (join-game [this client-id invite]
    "Join an existing game with ID `game-id` for client with ID `client-id`
  as an invitee.")
  (discover-code [this game-id word]
    "Discover the colour of agent with code name `word` in game
  with ID `game-id`."))

(defn- add-player [players game-id client-id]
  (-> players
      (update-in [:clients game-id] add-to-set client-id)
      (assoc-in [:game client-id] game-id)))

(deftype SimpleGameManager [word-provider game-store players]
  GameManager
  (create-game [this client-id lang first-colour]
    (let [board (board/make-board (words/get-words word-provider lang size)
                                  (generate-colours first-colour))
          game (game-store/add-game game-store board)]
      (swap! players add-player (:id game) client-id)
      game))
  (open-game [this client-id game-id]
    (when-let [game (game-store/get-game game-store game-id)]
      (swap! players add-player game-id client-id)
      game))
  (join-game [this client-id invite]
    (when-let [{role :type, game-id :game-id}
               (game-store/resolve-invite game-store invite)]
      (when-let [{:keys [board discovered-codes]}
                 (game-store/get-game game-store game-id)]
        (swap! players add-player game-id client-id)
        {:game {:board (if (and discovered-codes (not= :spymaster role))
                         (board/clear-undiscovered board)
                         board)
                :discovered-codes discovered-codes}
         :role role})))
  (discover-code [this game-id word]
    (when-let [colour (game-store/discover-word game-store game-id word)]
      {:colour colour, :word word}))
  event/EventHandler
  (handle-event [this {:keys [client-id message] :as event}]
    (case (get message 0)
      :create-game
      (let [[_ lang first-colour] message
            resp [:new-game (create-game this client-id lang first-colour)]]
        (ws/send-message client-id resp))
      :open-game
      (let [id (get message 1)
            resp [:opened-game (open-game this client-id id)]]
        (ws/send-message client-id resp))
      :join-game
      (let [invite (get message 1)
            resp [:joined-game (join-game this client-id invite)]]
        (ws/send-message client-id resp))
      :discover-code
      (when-let [game-id (get-in @players [:game client-id])]
        (let [word (get message 1)
              resp-params (discover-code this game-id word)
              resp [:discovered-code resp-params]]
          (doseq [player-id (get-in @players [:clients game-id])]
            (ws/send-message player-id resp))))
      (log/info "received event" event))))

(defmethod ig/init-key ::provider [_key config]
  (SimpleGameManager. (::word-provider config)
                      (::game-store config)
                      (atom {})))
