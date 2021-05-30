(ns github.bentomi.kodnevek.game
  (:require [integrant.core :as ig]
            [github.bentomi.kodnevek.words :as words]))

(def ^:private size 25)
(def ^:private reds 9)
(def ^:private blues 8)
(def ^:private whites 7)
(def ^:private blacks (- size reds blues whites))

(defn- generate-colours []
  (->> (concat (repeat reds :red) (repeat blues :blue)
               (repeat whites :white) (repeat blacks :black))
       shuffle))

(defprotocol GameManager
  (create-game [this lang]
    "Create a new game."))

(deftype SimpleGameManager [word-provider]
  GameManager
  (create-game [this lang]
    (map (fn [word colour] {:word word, :colour colour})
         (words/get-words word-provider lang size)
         (generate-colours))))

(defmethod ig/init-key ::provider [_key config]
  (SimpleGameManager. (::words/provider config)))
