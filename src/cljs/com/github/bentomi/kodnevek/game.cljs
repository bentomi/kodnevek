(ns com.github.bentomi.kodnevek.game
  "Game handling logic for the frontend."
  (:require [re-frame.core :as re-frame]
            [com.github.bentomi.kodnevek.board :as board]
            [com.github.bentomi.kodnevek.navigation :as navi]
            [com.github.bentomi.kodnevek.ws :as ws]))

(re-frame/reg-event-db
 ::open
 (fn [db [_ game-id]]
   (assoc db ::open-id game-id)))

(re-frame/reg-event-db
 ::join
 (fn [db [_ invite]]
   (assoc db ::invite invite)))

(re-frame/reg-event-db
  ::set-role
  (fn [db [_ role]]
    (assoc db ::role role)))

(re-frame/reg-event-db
  ::set-game
  (fn [db [_ result]]
    (assoc db ::game result)))

(re-frame/reg-event-fx
 ::discover-code
 (fn [_ [_ word]]
   (let [event [:discover-code word]]
     {:dispatch (ws/send-message-event event)})))

(defn- add-discovered-code [db [_ {:keys [word colour]}]]
  (if-let [index (:index (board/find-word (-> db ::game :board) word))]
    (-> db
        (update-in [::game :discovered-codes] (fnil conj #{}) word)
        (assoc-in [::game :board index :colour] colour))
    db))

(re-frame/reg-event-db ::add-discovered-code add-discovered-code)

(defn- coloured-words [db]
  (when-let [board (-> db ::game :board)]
    (let [size (count board)
          discovered-codes (get-in db [::game :discovered-codes] #{})]
      (->> board
           (map #(assoc % :discovered? (contains? discovered-codes (:word %))))
           (partition-all (-> size js/Math.sqrt js/Math.ceil))))))

(re-frame/reg-sub ::coloured-words coloured-words)

(re-frame/reg-sub
 ::id
 (fn [db]
   (-> db ::game :id)))

(re-frame/reg-sub
 ::spymaster-invite
 (fn [db]
   (-> db ::game :spymaster-invite)))

(re-frame/reg-sub
 ::agent-invite
 (fn [db]
   (-> db ::game :agent-invite)))

(re-frame/reg-sub
 ::role
 (fn [db]
   (::role db)))

(re-frame/reg-event-fx
 ::new-session
 (fn [{db :db} [_]]
   (let [{::keys [open-id invite]} db]
     (cond open-id {:dispatch (ws/send-message-event [:open-game open-id])}
           invite {:dispatch (ws/send-message-event [:join-game invite])}))))

(re-frame/reg-event-fx
 ::new-game
 (fn [_ [_ lang first-colour]]
   {:dispatch (ws/send-message-event [:create-game lang first-colour])}))

(defn handle-event [event]
  (case (get event 0)
    :new-session (re-frame/dispatch [::new-session])
    :new-game (let [game (get event 1)
                    game-id (:id game)]
                (re-frame/dispatch [::set-game game])
                (re-frame/dispatch [::open game-id])
                (re-frame/dispatch [::navi/go-to
                                    (navi/open game-id)
                                    (ws/send-message-event
                                     [:open-game game-id])]))
    :opened-game (re-frame/dispatch [::set-game (get event 1)])
    :joined-game (let [{:keys [game role]} (get event 1)]
                   (re-frame/dispatch [::set-role role])
                   (re-frame/dispatch [::set-game game]))
    :discovered-code (re-frame/dispatch [::add-discovered-code (get event 1)])
    #(js/console.info event)))
