(ns github.bentomi.kodnevek.in-mem-game-store
  (:require [integrant.core :as ig]
            [github.bentomi.kodnevek.board :as board]
            [github.bentomi.kodnevek.game-store :as store]
            [github.bentomi.kodnevek.util :refer [add-to-set]]))

(defn- fresh-key [key-generator taken-pred]
  (loop [k (key-generator)]
    (if (taken-pred k)
      (recur (key-generator))
      k)))

(defn- add-to-map [m key-generator v]
  (loop [k (key-generator)]
    (if (contains? m k)
      (recur (key-generator))
      [k (assoc m k v)])))

(defn- store-game
  [{:keys [games invites] :as store} key-generator board]
  (let [invite-taken? (partial contains? invites)
        agent-invite (fresh-key key-generator invite-taken?)
        spymaster-invite (fresh-key key-generator #(or (invite-taken? %)
                                                       (= agent-invite %)))
        game-id (fresh-key key-generator (partial contains? games))
        game {:id game-id
              :board board
              :spymaster-invite spymaster-invite
              :agent-invite agent-invite}]
    [game
     (-> store
         (update :games assoc game-id game)
         (update :invites assoc
                 agent-invite {:type :agent, :game-id game-id}
                 spymaster-invite {:type :spymaster, :game-id game-id}))]))

(deftype InMemoryGameStore [key-generator store]
  store/GameStore
  (add-game [this board]
    (loop [s @store]
      (let [[game s'] (store-game s key-generator board)]
        (if (compare-and-set! store s s')
          game
          (recur @store)))))
  (get-game [this id]
    (get-in @store [:games id]))
  (resolve-invite [this invite]
    (get-in @store [:invites invite]))
  (discover-word [this game-id word]
    (swap! store update-in [:games game-id :discovered-codes] add-to-set word)
    (:colour (board/find-word (get-in @store [:games game-id :board]) word))))

(defmethod ig/init-key ::provider [_key config]
  (InMemoryGameStore. (::key-generator config) (atom {})))
