(ns github.bentomi.kodnevek.main
  "The entry point of the frontend and the main page."
  (:require [reagent.dom :as rdom]
            [ajax.core :as ajax]
            [re-frame.core :as re-frame]
            [day8.re-frame.http-fx]
            [github.bentomi.kodnevek.errors :as errors]
            [github.bentomi.kodnevek.game :as game]
            [github.bentomi.kodnevek.navigation :as navi]
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

(defn- invites []
  [:ul.invites
   (when-let [id @(re-frame/subscribe [::game/id])]
     [:li.id
      [:span "Game ID: "]
      [:a {:href (navi/open id)} id]])
   (when-let [invite @(re-frame/subscribe [::game/agent-invite])]
     [:li.agent-invite
      [:span "Agent invite: "]
      [:a {:href (navi/join invite)} invite]])
   (when-let [invite @(re-frame/subscribe [::game/spymaster-invite])]
     [:li.spymaster-invite
      [:span "Spymaster invite: "]
      [:a {:href (navi/join invite)} invite]])])

(defn- controls []
  (let [lang @(re-frame/subscribe [::lang])
        new-game #(re-frame/dispatch [::game/new-game lang %])]
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
     [:li.new_game
      [:label "New game:"]
      [:button.red {:on-click #(new-game :red)} "Red starts"]
      [:button.blue {:on-click #(new-game :blue)} "Blue starts"]]
     (when (or @(re-frame/subscribe [::game/id])
               (= :spymaster @(re-frame/subscribe [::game/role])))
       [:li.spymaster
        [:label {:for "sypmaster"} "Spymaster:"]
        (let [spymaster? @(re-frame/subscribe [::spymaster?])]
          [:input {:id "spymaster"
                   :type :checkbox
                   :on-change #(let [flag (.. % -target -checked)]
                                 (re-frame/dispatch [::set-spymaster flag]))
                   :checked spymaster?}])])]))

(defn- errors []
  (when-let [error @(re-frame/subscribe [::errors/error])]
    [:div.error {:on-click #(re-frame/dispatch [::errors/clear-error])}
     (str error)]))

(defn- main-panel []
  [:<>
   [board]
   [invites]
   [controls]
   [errors]])

(defn ^:dev/after-load mount-root [root-el]
  (re-frame/clear-subscription-cache!)
  (rdom/unmount-component-at-node root-el)
  (rdom/render [main-panel] root-el))

(defn init []
  (let [root-el (.getElementById js/document "app")
        game-id (.getAttribute root-el "data-game-id")
        invite (.getAttribute root-el "data-invite")
        scheme (if (= "https:" js/location.protocol) "wss" "ws")]
    (cond game-id (re-frame/dispatch [::game/open game-id])
          invite (re-frame/dispatch [::game/join invite]))
    (ws/connect (str scheme "://" js/location.host "/ws")
                game/handle-event)
    (re-frame/dispatch [::retrieve-languages])
    (navi/init)
    (mount-root root-el)))

(init)
