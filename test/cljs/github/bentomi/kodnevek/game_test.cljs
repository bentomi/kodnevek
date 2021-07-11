(ns github.bentomi.kodnevek.game-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.spec.alpha :as spec]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.generators :as chuck-gen]
            [com.gfredericks.test.chuck.clojure-test :refer-macros [checking]]
            [github.bentomi.kodnevek.global-specs :as gs]
            [github.bentomi.kodnevek.game :as g]))

(deftest unknown-word-colouring-test
  (checking "unknown words don't change"
   [words (gen/vector-distinct (spec/gen ::gs/word) {:min-elements 1})
    colour (spec/gen ::gs/colour)]
   (let [board (mapv #(-> {:word %}) (pop words))
         db {::g/game {:board board}}
         discovered-word (words (dec (count words)))
         event-vec [::g/add-discovered-code {:word discovered-word
                                             :colour colour}]
         db' (#'g/add-discovered-code db event-vec)]
     (is (= db db')))))

(deftest known-word-colouring-test
  (checking "known words colour are remembered"
   [[words discovered-word]
    (gen/bind (gen/vector-distinct (spec/gen ::gs/word) {:min-elements 1})
              #(gen/tuple (gen/return %) (gen/elements %)))
    colour (spec/gen ::gs/colour)]
   (let [board (mapv #(-> {:word %}) words)
         db {::g/game {:board board}}
         event-vec [::g/add-discovered-code {:word discovered-word
                                             :colour colour}]
         db' (#'g/add-discovered-code db event-vec)]
     (is (= colour (some #(when (= discovered-word (:word %))
                            (:colour %))
                         (-> db' ::g/game :board))))
     (is (= #{discovered-word} (-> db' ::g/game :discovered-codes))))))

(deftest coloured-words-test
  (checking "coloured words have colours and are partitioned"
   [[words colours discovered-words]
    (gen/bind (gen/vector-distinct (spec/gen ::gs/word) {:min-elements 1})
              #(gen/tuple (gen/return %)
                          (gen/vector (spec/gen ::gs/colour) (count %))
                          (chuck-gen/subsequence %)))]
   (let [board (mapv #(-> {:word %1, :colour %2}) words colours)
         discovered-codes (set discovered-words)
         db {::g/game {:board board, :discovered-codes discovered-codes}}
         coloured-words (#'g/coloured-words db)
         columns (-> board count js/Math.sqrt js/Math.ceil)
         expected (->> board
                       (map #(assoc % :discovered?
                                    (contains? discovered-codes (:word %))))
                       (partition-all columns))]
     (is (= expected coloured-words)))))
