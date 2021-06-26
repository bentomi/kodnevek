(ns github.bentomi.kodnevek.board
  "Abstraction representing the game board.")

(defn make-field [word colour]
  {:word word
   :colour colour})

(defn make-board [words colours]
  (mapv make-field words colours))

(defn clear-undiscovered [board discovered-pred]
  (mapv #(if (discovered-pred (:word %))
           %
           (dissoc % :colour))
        board))

(defn find-word [board word-to-find]
  (some #(let [field (board %)]
           (when (= word-to-find (:word field))
             (assoc field :index %)))
        (range (count board))))
