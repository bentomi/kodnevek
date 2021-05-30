(ns github.bentomi.kodnevek.main
  (:require [reagent.dom :as rdom]
            [ajax.core :as ajax]
            [re-frame.core :as re-frame]
            [day8.re-frame.http-fx]
            [github.bentomi.kodnevek.errors :as errors]
            [github.bentomi.kodnevek.game :as game]
            [github.bentomi.kodnevek.ws :as ws]))

(re-frame/reg-event-db
  ::retrieve-languages-success
  (fn [db [_ result]]
    (assoc db ::languages result)))

(re-frame/reg-event-db
  ::select-language
  (fn [db [_ lang]]
    (assoc db ::lang lang)))

(re-frame/reg-event-db
 ::set-spymaster
 (fn [db [_ spymaster?]]
   (assoc db ::spymaster? spymaster?)))

(re-frame/reg-event-fx
 ::retrieve-languages
 (fn [_ _]
   {:http-xhrio {:method :get
                 :uri "/languages"
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [::retrieve-languages-success]
                 :on-failure [::errors/http-error "languages"]}}))

(re-frame/reg-sub
 ::languages
 (fn [db]
   (::languages db)))

(re-frame/reg-sub
 ::lang
 (fn [db]
   (or (::lang db) (first (::languages db)) "en")))

(re-frame/reg-sub
 ::spymaster?
 (fn [db]
   (::spymaster? db false)))

(defn- field [spymaster? {:keys [word colour discovered?]}]
  (let [card-colour (if (or spymaster? discovered?) colour :clear)]
    [:td {:class (if spymaster? colour :clear)}
     [:div.card {:class (when discovered? :discovered)
                 :on-click #(re-frame/dispatch [::game/discover-code word])}
      [:div.card__face.card__face--front {:class card-colour} word]
      [:div.card__face.card__face--back {:class card-colour}]]]))

(defn- board []
  (when-let [words (seq @(re-frame/subscribe [::game/coloured-words]))]
    (let [spymaster? @(re-frame/subscribe [::spymaster?])]
      [:div>table.board
       (into [:tbody]
             (map #(into [:tr] (map (partial field spymaster?)) %))
             words)])))

(defn- controls []
  (let [lang @(re-frame/subscribe [::lang])]
    [:ul.controls
     (when-let [languages @(re-frame/subscribe [::languages])]
       [:li.lang_selector
        [:label {:for "language"} "Language:"]
        (into [:select {:id "language"
                        :on-change #(re-frame/dispatch
                                     [::select-language (.. % -target -value)])
                        :value lang}]
              (map #(-> [:option {:value %} %]))
              languages)])
     [:li.new
      [:button {:on-click #(re-frame/dispatch [::game/new-game lang])}
       "New game"]]
     [:li.spymaster
      [:label {:for "sypmaster"} "Spymaster:"]
      (let [spymaster? @(re-frame/subscribe [::spymaster?])]
        [:input {:id "spymaster"
                 :type :checkbox
                 :checked spymaster?
                 :on-change #(re-frame/dispatch
                              [::set-spymaster (.. % -target -checked)])}])]]))

(defn- errors []
  (when-let [error @(re-frame/subscribe [::errors/error])]
    [:div.error {:on-click #(re-frame/dispatch [::errors/clear-error])}
     (str error)]))

(defn- main-panel []
  [:<>
   [board]
   [controls]
   [errors]])

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [main-panel] root-el)))

(defn init []
  (ws/connect (str "ws://" (.. js/document -location -host) "/ws")
              game/handle-event)
  (re-frame/dispatch [::retrieve-languages])
  (mount-root))

(init)
