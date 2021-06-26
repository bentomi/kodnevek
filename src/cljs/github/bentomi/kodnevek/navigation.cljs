(ns github.bentomi.kodnevek.navigation
  "Navigation history handling."
  (:require [re-frame.core :as re-frame]
            [cljs.reader :refer [read-string]]))

(defn open [game-id]
  (str "open?id=" game-id))

(defn join [invite]
  (str "join?id=" invite))

(re-frame/reg-fx
 :navigate
 (fn [{:keys [state title url]}]
   (.pushState js/history (pr-str state) title url)))

(re-frame/reg-event-fx
 ::go-to
 (fn [_ [_ url event]]
   {:navigate {:url url, :state event}}))

(defn init []
  (set! (.-onpopstate js/window)
        #(let [event (.-state %)]
           (when-let [event (and (string? event) (read-string event))]
             (re-frame/dispatch event)))))
