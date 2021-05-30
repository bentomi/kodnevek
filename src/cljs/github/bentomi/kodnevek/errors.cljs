(ns github.bentomi.kodnevek.errors
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-event-db
  ::http-error
  (fn [db [_ query result]]
    (assoc db ::error {:type query, :result result})))

(re-frame/reg-event-db
  ::no-session-error
  (fn [db [_ command]]
    (assoc db ::error {:type "no session"
                       :result (str "Could not " command)})))

(re-frame/reg-event-db
  ::clear-error
  (fn [db _]
    (dissoc db ::error)))

(re-frame/reg-sub
 ::error
 (fn [db]
   (::error db)))
