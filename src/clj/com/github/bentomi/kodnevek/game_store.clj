(ns com.github.bentomi.kodnevek.game-store
  "Interface for storing games."
  (:require [integrant.core :as ig]))

(defprotocol GameStore
  (add-game [this board]
    "Add a new game to the store and return it with a unique id plus spymaster
  and agent references.")
  (get-game [this id]
    "Return the game with ID `id`.")
  (resolve-invite [this invite]
    "Return the type and game of the for the invite `invite`.")
  (discover-word [this game-id word]
    "Discover code `word` in game with ID `game-id` and return its colour. "))
