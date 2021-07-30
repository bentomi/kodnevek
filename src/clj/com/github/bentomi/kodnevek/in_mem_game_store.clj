(ns com.github.bentomi.kodnevek.in-mem-game-store
  "An implementation of the `GameStore` interface storing games in memory."
  (:require [integrant.core :as ig]
            [com.github.bentomi.kodnevek.board :as board]
            [com.github.bentomi.kodnevek.game-store :as store]
            [com.github.bentomi.kodnevek.util :refer [add-to-set]]))

(defn- fresh-key [key-generator taken-pred]
  (loop [k (key-generator)]
    (if (taken-pred k)
      (recur (key-generator))
      k)))

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
  (add-game [_this board]
    (loop [s @store]
      (let [[game s'] (store-game s key-generator board)]
        (if (compare-and-set! store s s')
          game
          (recur @store)))))
  (get-game [_this id]
    (get-in @store [:games id]))
  (resolve-invite [this invite]
    (when-let [{:keys [type game-id]} (get-in @store [:invites invite])]
      {:type type
       :game (store/get-game this game-id)}))
  (discover-word [_this game-id word]
    (swap! store update-in [:games game-id :discovered-codes] add-to-set word)
    (:colour (board/find-word (get-in @store [:games game-id :board]) word))))

(defmethod ig/init-key ::provider [_key config]
  (InMemoryGameStore. (::key-generator config) (atom {})))
