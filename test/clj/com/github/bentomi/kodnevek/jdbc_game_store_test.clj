(ns com.github.bentomi.kodnevek.jdbc-game-store-test
  (:require [clojure.test :as test :refer [deftest is]]
            [clojure.spec.alpha :as spec]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [com.github.bentomi.kodnevek.global-specs :as gs]
            [com.github.bentomi.kodnevek.util :as util]
            [com.github.bentomi.kodnevek.game-store :as store]
            [com.github.bentomi.kodnevek.jdbc-game-store :as game-store])
  (:import (java.util.concurrent Callable Executors TimeUnit)))

(defn- key-generator []
  (str (java.util.UUID/randomUUID)))

(def ^:private db-spec {:dbtype "h2:mem" :dbname (key-generator)})

(defn- ->game-store []
  (game-store/->game-store {::game-store/key-generator key-generator
                            ::game-store/db-spec db-spec}))

(defn- normalize-game [game]
  (-> game
      (dissoc :version)
      (util/assoc-missing :discovered-codes #{})))

(defn- check-invite [expected actual]
  (is (= (:type expected) (:type actual)))
  (let [normalized-expected (normalize-game (:game expected))
        normalized-actual (normalize-game (:game actual))]
    (is (= normalized-expected normalized-actual))))

(defn- check-game [gs]
  (checking "game-store operations"
   [[words colours]
    (gen/bind (gen/set (spec/gen ::gs/word) {:min-elements 25})
              #(gen/tuple (gen/return %)
                          (gen/vector (spec/gen ::gs/colour) (count %))))]
   (let [board (mapv #(-> {:word %1, :colour %2}) words colours)
         game (store/add-game gs board)
         game-id (:id game)]
     (is (string? game-id))
     (is (spec/valid? ::gs/game game))
     (check-invite {:type :agent, :game game}
                   (store/resolve-invite gs (:agent-invite game)))
     (check-invite {:type :spymaster, :game game}
                   (store/resolve-invite gs (:spymaster-invite game)))
     (loop [undiscovered words, discovered #{}]
       (let [game (store/get-game gs game-id)]
         (is (spec/valid? ::gs/game game))
         (is (= discovered (:discovered-codes game)))
         (when-let [w (first undiscovered)]
           (store/discover-word gs game-id w)
           (recur (rest undiscovered) (conj discovered w))))))))

(deftest single-thread-test
  (with-open [gs (->game-store)]
    (check-game gs)))

(deftest multi-thread-test
  (with-open [gs (->game-store)]
    (let [threads 6
          games (+ threads 4)
          tp (Executors/newFixedThreadPool threads)
          test-game (fn [] (.submit tp ^Callable #(check-game gs)))
          futures (into [] (repeatedly games test-game))]
      (run! #(.get % 1 TimeUnit/MINUTES) futures))))
