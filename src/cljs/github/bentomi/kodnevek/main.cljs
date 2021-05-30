(ns github.bentomi.kodnevek.main
  (:require [reagent.dom :as rdom]
            [ajax.core :as ajax]
            [re-frame.core :as re-frame]
            [day8.re-frame.http-fx]))

(re-frame/reg-event-db
  ::retrieve-languages-success
  (fn [db [_ result]]
    (assoc db :languages result)))

(re-frame/reg-event-db
  ::create-game-success
  (fn [db [_ result]]
    (assoc db :game result)))

(re-frame/reg-event-db
  ::select-language
  (fn [db [_ lang]]
    (assoc db :lang lang)))

(re-frame/reg-event-db
  ::http-error
  (fn [db [_ query result]]
    (assoc db :error {:type query, :result result})))

(re-frame/reg-event-db
 ::set-spymaster
 (fn [db [_ spymaster?]]
   (assoc db :spymaster? spymaster?)))

(re-frame/reg-event-db
 ::discover-code
 (fn [db [_ word]]
   (update db :discovered-codes (fnil conj #{}) word)))

(re-frame/reg-event-fx
 ::retrieve-languages
 (fn [_ _]
   {:http-xhrio {:method :get
                 :uri "/languages"
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [::retrieve-languages-success]
                 :on-failure [::http-error "languages"]}}))

(re-frame/reg-event-fx
 ::create-game
 (fn [{db :db} [_ lang]]
   (let [lang (or lang (:lang db) (first (:languages db)) "en")]
     {:http-xhrio {:method :get
                   :uri "/create-game"
                   :params {:lang lang}
                   :response-format (ajax/json-response-format {:keywords? true})
                   :on-success [::create-game-success]
                   :on-failure [::http-error "words"]}})))

(re-frame/reg-event-fx
 ::new-game
 (fn [{db :db} _]
   {:db (assoc db :discovered-codes #{})
    :dispatch [::create-game]}))

(re-frame/reg-sub
 ::languages
 (fn [db]
   (:languages db)))

(re-frame/reg-sub
 ::error
 (fn [db]
   (:error db)))

(re-frame/reg-sub
 ::lang
 (fn [db]
   (:lang db)))

(re-frame/reg-sub
 ::spymaster?
 (fn [db]
   (:spymaster? db false)))

(re-frame/reg-sub
 ::coloured-words
 (fn [db]
   (when-let [game (:game db)]
     (let [size (count game)
           discovered-codes (:discovered-codes db #{})]
       (->> game
            (map #(assoc % :discovered? (contains? discovered-codes (:word %))))
            (partition-all (-> size js/Math.sqrt int)))))))

(defn- field [spymaster? {:keys [word colour discovered?]}]
  (let [card-colour (if (or spymaster? discovered?) colour :clear)]
    [:td {:class (if spymaster? colour :clear)}
     [:div.card {:class (when discovered? :discovered)
                 :on-click #(re-frame/dispatch [::discover-code word])}
      [:div.card__face.card__face--front {:class card-colour} word]
      [:div.card__face.card__face--back {:class card-colour}]]]))

(defn- board []
  (when-let [words (seq @(re-frame/subscribe [::coloured-words]))]
    (let [spymaster? @(re-frame/subscribe [::spymaster?])]
      [:div>table.board
       (into [:tbody]
             (map #(into [:tr] (map (partial field spymaster?)) %))
             words)])))

(defn- controls []
  [:ul.controls
    (when-let [languages @(re-frame/subscribe [::languages])]
      [:li.lang_selector
       [:label {:for "language"} "Language:"]
       (let [selected-lang @(re-frame/subscribe [::lang])]
         (into [:select {:id "language"
                         :on-change #(re-frame/dispatch
                                      [::select-language (.. % -target -value)])
                         :value (or selected-lang (first languages))}]
               (map #(-> [:option {:value %} %]))
               languages))])
    [:li.new
     [:button {:on-click #(re-frame/dispatch [::new-game])}
      "New game"]]
    [:li.spymaster
     [:label {:for "sypmaster"} "Spymaster:"]
     (let [spymaster? @(re-frame/subscribe [::spymaster?])]
       [:input {:id "spymaster"
                :type :checkbox
                :checked spymaster?
                :on-change #(re-frame/dispatch
                             [::set-spymaster (.. % -target -checked)])}])]])

(defn- errors []
  (when-let [error @(re-frame/subscribe [::error])]
    [:div.error
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
  (re-frame/dispatch [::retrieve-languages])
  (re-frame/dispatch [::new-game])
  (mount-root))

(init)
