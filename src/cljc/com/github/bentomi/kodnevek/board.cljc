(ns com.github.bentomi.kodnevek.board
  "Abstraction representing the game board."
  (:require [clojure.spec.alpha :as spec]
            [com.github.bentomi.kodnevek.global-specs :as gs]))

(spec/fdef make-field
  :args (spec/cat :word ::gs/word :colour (spec/nilable ::gs/colour))
  :ret ::gs/field)

(defn make-field [word colour]
  (cond-> {:word word}
    (some? colour) (assoc :colour colour)))

(spec/fdef make-board
  :args (spec/cat :words (spec/coll-of ::gs/word :distinct true)
                  :colours (spec/coll-of (spec/nilable ::gs/colour)))
  :ret ::gs/board)

(defn make-board [words colours]
  (mapv make-field words colours))

(spec/fdef clear-undiscovered
  :args (spec/cat
         :board ::gs/board
         :discovered-pred (spec/with-gen
                            (spec/fspec :args (spec/tuple ::gs/word))
                            #(spec/gen (spec/coll-of ::gs/word :kind set?))))
  :ret ::gs/board)

(defn clear-undiscovered [board discovered-pred]
  (mapv #(if (discovered-pred (:word %))
           %
           (dissoc % :colour))
        board))

(spec/fdef find-word
  :args (spec/cat :board ::gs/board :word ::gs/word)
  :ret (spec/nilable (spec/and ::gs/field #(-> % :index int?))))

(defn find-word [board word-to-find]
  (some #(let [field (board %)]
           (when (= word-to-find (:word field))
             (assoc field :index %)))
        (range (count board))))
