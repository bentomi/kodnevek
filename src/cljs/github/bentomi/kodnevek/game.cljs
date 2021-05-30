(ns github.bentomi.kodnevek.game
  (:require [re-frame.core :as re-frame]
            [github.bentomi.kodnevek.errors :as errors]
            [github.bentomi.kodnevek.ws :as ws]))

(re-frame/reg-event-db
  ::set-game
  (fn [db [_ result]]
    (assoc db ::game result)))

(re-frame/reg-event-db
 ::discover-code
 (fn [db [_ word]]
   (update db ::discovered-codes (fnil conj #{}) word)))

(re-frame/reg-sub
 ::coloured-words
 (fn [db]
   (when-let [game (::game db)]
     (let [size (count game)
           discovered-codes (::discovered-codes db #{})]
       (->> game
            (map #(assoc % :discovered? (contains? discovered-codes (:word %))))
            (partition-all (-> size js/Math.sqrt int)))))))

(re-frame/reg-event-fx
 ::new-game
 (fn [{db :db} [_ lang]]
   {:db (assoc db ::discovered-codes #{})
    :dispatch [::ws/send-message
               [:create-game lang]
               [::errors/no-session-error "create game"]]}))

(defn handle-event [event]
  (case (get event 0)
    :new-game (re-frame/dispatch [::set-game (get event 1)])
    #(js/console.info event)))
