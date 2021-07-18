(ns com.github.bentomi.kodnevek.game-test
  (:require [clojure.test :as test :refer [deftest is]]
            [clojure.spec.alpha :as spec]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :refer [defspec]]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [com.github.bentomi.kodnevek.event :as event]
            [com.github.bentomi.kodnevek.in-mem-game-store :as game-store]
            [com.github.bentomi.kodnevek.global-specs :as gs]
            [com.github.bentomi.kodnevek.util :as util]
            [com.github.bentomi.kodnevek.words :as words]
            [com.github.bentomi.kodnevek.ws :as ws]
            [com.github.bentomi.kodnevek.game :as g]))

(defn- key-generator []
  (str (java.util.UUID/randomUUID)))

(deftype DummyWordProvider [words]
  words/WordProvider
  (get-languages [_this] "en")
  (get-words [_this _lang size]
    (->> words cycle (take size) vec)))

(deftype DummyMessageSender [messages]
  ws/MessageSender
  (send-message [this client-id message]
    (swap! messages update client-id (fnil conj []) message)))

(defn- ->game-manager [words messages]
  (let [games (atom {})
        players (atom {})]
    (g/->SimpleGameManager
     (->DummyWordProvider words)
     (game-store/->InMemoryGameStore key-generator games)
     (->DummyMessageSender messages)
     players)))

(defn- is-valid-game [words game]
  (is (spec/valid? ::gs/game game) "game corresponds to spec")
  (is (every? words (map :word (:board game)))
      "words of the game are all expected"))

(defn- is-valid-create-response [words [new-message-type game]]
  (let [{game-id :id :keys [agent-invite spymaster-invite]} game]
    (is (= :new-game new-message-type) "create response is new-game")
    (is-valid-game words game)
    (is (some? game-id) "game ID is set")
    (is (some? agent-invite) "agent invite is set")
    (is (some? spymaster-invite) "spymaster invite is set")))

(defn- send-share-events [gm game share-infos]
  (let [{game-id :id :keys [agent-invite spymaster-invite]} game]
    (doseq [share-info share-infos]
      (let [event (if (vector? share-info)
                    (let [[id role] share-info]
                      {:client-id id
                       :message [:join-game (if (= :agent role)
                                              agent-invite
                                              spymaster-invite)]})
                    {:client-id share-info
                     :message [:open-game game-id]})]
        (event/handle-event gm event)))))

(defn- are-valid-share-messages
  [game
   {:keys [openers joiners discovered-codes] :or {discovered-codes #{}}}
   messages]
  (doseq [[id events] messages
          [t g] events]
    (case t
      :opened-game
      (do (is (contains? openers id))
          (is (= game g)))
      :joined-game
      (do (is (contains? joiners id))
          (is (spec/valid? ::gs/game (:game g)))
          (is (= (map :word (:board game))
                 (map :word (-> g :game :board))))
          (let [role (:role g)
                known-code? #(or (= :spymaster role)
                                 (contains? discovered-codes %))
                known-colours-xform (comp (filter (comp known-code? :word))
                                          (map :colour))]
            (is (spec/valid? ::gs/role role))
            (is (= (sequence known-colours-xform (:board game))
                   (sequence known-colours-xform (-> g :game :board))))))
      (is false "expected one of :opened-game or :joined-game"))))

(defn- is-valid-discovered-message
  [game discovered-word [message-type {:keys [word colour]}]]
  (is (= :discovered-code message-type))
  (is (= discovered-word word))
  (is (= (some #(when (= discovered-word (:word %))
                  (:colour %))
               (:board game))
         colour)))

(defn- are-valid-discover-messages [game receivers discovered-word messages]
  (doseq [c receivers]
    (let [[discovered-message & other-messages] (get messages c)]
      (is (nil? other-messages))
      (is-valid-discovered-message game discovered-word discovered-message))))

(def ^:private id-gen (spec/gen ::gs/id))
(def ^:private role-gen (spec/gen ::gs/role))

(deftest single-game-test
  (checking "a single game can be shared and played"
   [first-colour (gen/elements #{:blue :red})
    creator (spec/gen ::gs/id)
    share-infos (gen/vector (gen/one-of [id-gen (gen/tuple id-gen role-gen)]))
    words (gen/set (spec/gen ::gs/word) {:min-elements 25})
    discover-count (gen/large-integer* {:min 1, :max 25})]
   (let [messages (atom {})
         gm (->game-manager words messages)
         openers (into #{} (remove vector?) share-infos)
         joiners (into #{} (comp (filter vector?) (map first)) share-infos)
         added-clients (into openers joiners)
         clients (conj added-clients creator)
         receivers {:openers openers, :joiners joiners}
         discover-words (take discover-count words)]
     ;; create a new game
     (event/handle-event gm {:client-id creator
                             :message [:create-game "en" first-colour]})
     (let [create-response (get-in @messages [creator 0])
           [_ {game-id :id :keys [agent-invite spymaster-invite] :as game}]
           create-response]
       (is-valid-create-response words create-response)
       ;; share the pristine game
       (reset! messages {})
       (send-share-events gm game share-infos)
       (is (= added-clients (set (keys @messages))))
       (is (= (count share-infos) (count (mapcat val @messages))))
       (are-valid-share-messages game receivers @messages)
       ;; discover some words
       (doseq [[w c] (map vector discover-words (cycle clients))]
         (reset! messages {})
         (event/handle-event gm {:client-id c
                                 :message [:discover-code w]})
         (are-valid-discover-messages game clients w @messages))
       ;; share the game again with some words discovered
       (reset! messages {})
       (send-share-events gm game share-infos)
       (is (= added-clients (set (keys @messages))))
       (is (= (count share-infos) (count (mapcat val @messages))))
       (let [discovered-set (set discover-words)]
         (are-valid-share-messages
          (assoc game :discovered-codes discovered-set)
          (assoc receivers :discovered-codes discovered-set)
          @messages))))))
