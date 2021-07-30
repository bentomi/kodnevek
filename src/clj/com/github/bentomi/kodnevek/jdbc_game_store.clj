(ns com.github.bentomi.kodnevek.jdbc-game-store
  "An implementation of the `GameStore` interface storing games in
  a JDBC store."
  (:require [clojure.edn :as edn]
            [medley.core :as m]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [next.jdbc.sql :as sql]
            [com.github.bentomi.kodnevek.board :as board]
            [com.github.bentomi.kodnevek.game-store :as store]
            [com.github.bentomi.kodnevek.util :as util])
  (:import (java.sql SQLIntegrityConstraintViolationException)
           (com.zaxxer.hikari HikariDataSource)))

(defn- init-database [ds]
  (jdbc/execute! ds ["
create table if not exists id (
  id varchar primary key)"])
  (jdbc/execute! ds ["
create table if not exists game (
  id varchar primary key,
  board varchar,
  discovered_codes varchar,
  agent_invite varchar unique,
  spymaster_invite varchar unique,
  version int)"]))

(defn- generate-key [key-generator ds]
  (->> #(try
          (let [k (key-generator)]
            (jdbc/execute-one! ds ["insert into id (id) values (?)" k])
            k)
          (catch SQLIntegrityConstraintViolationException _))
       repeatedly
       (some identity)))

(def ^:private complex-fields #{:board :discovered-codes})

(defn- update-complex-fields [game updater]
  (reduce (fn [game field] (m/update-existing game field updater))
          game
          complex-fields))

(defn- serialize-game [game]
  (update-complex-fields game pr-str))

(defn- deserialize-game [game]
  (update-complex-fields game edn/read-string))

(defn- insert-game [ds game]
  (let [stored-game
        (-> game
            (util/assoc-missing :discovered-codes #{} :version 0)
            serialize-game)]
    (sql/insert! ds :game stored-game jdbc/unqualified-snake-kebab-opts)))

(defn- find-game [ds query]
  (deserialize-game
   (first (jdbc/execute! ds query jdbc/unqualified-snake-kebab-opts))))

(defn- update-game [ds game where-params]
  (sql/update! ds :game (serialize-game game) where-params
               jdbc/unqualified-snake-kebab-opts ))

(defn- find-game-by-id
  ([ds id]
   (find-game-by-id ds id "*"))
  ([ds id fields]
   (find-game ds [(format "select %s from game where id = ?" fields) id])))

(defn- find-game-by-invite [ds invite]
  (let [query "select * from game where ? in (agent_invite,spymaster_invite)"]
    (find-game ds [query invite])))

(defn- add-discovered-code [ds game-id word]
  (when-let [{:keys [discovered-codes version]}
             (find-game-by-id ds game-id "discovered_codes,version")]
    (when (-> (update-game ds
                           {:discovered-codes (conj discovered-codes word)
                            :version (inc version)}
                           {:id game-id
                            :version version})
              ::jdbc/update-count
              zero?)
      (recur ds game-id word))))

(deftype JdbcGameStore [key-generator ^HikariDataSource ds]
  store/GameStore
  (add-game [_this board]
    (let [ids (zipmap [:id :agent-invite :spymaster-invite]
                      (repeatedly #(generate-key key-generator ds)))
          game (assoc ids :board board)]
      (insert-game ds game)
      game))
  (get-game [_this id]
    (find-game-by-id ds id))
  (resolve-invite [_this invite]
    (when-let [game (find-game-by-invite ds invite)]
      {:type (if (= invite (:agent-invite game))
               :agent
               :spymaster)
       :game game}))
  (discover-word [_this game-id word]
    (when-let [{:keys [board]} (find-game-by-id ds game-id "board")]
      (add-discovered-code ds game-id word)
      (:colour (board/find-word board word))))
  java.io.Closeable
  (close [_this]
    (.close ds)))

(comment
  (def db-spec {:dbtype "h2" :dbname "example"})

  (def ^HikariDataSource ds (connection/->pool HikariDataSource db-spec))

  (let [rng (java.util.Random. 0)]
    (defn- key-generator []
      (-> rng .nextInt Math/abs (Integer/toString 36))))

  (jdbc/execute! ds ["drop table if exists id"])
  (jdbc/execute! ds ["drop table if exists game"])
  (init-database ds)

  (generate-key key-generator ds)

  (loop [i 0]
    (let [r (try
              (let [k (key-generator)]
                (jdbc/execute-one! ds ["insert into id (id) values (?)" k])
                k)
            (catch Exception e
              e))]
      (if (instance? SQLIntegrityConstraintViolationException r)
        (recur (inc i))
        [i r])))

  (jdbc/execute! ds ["select * from id"] jdbc/unqualified-snake-kebab-opts)

  (let [long-word (apply str (repeat 5000 "hello"))
        game {:id (key-generator)
              :board (pr-str [{:word "word" :colour :red}
                              {:word "discovered" :colour :white}])
              :discovered-codes (pr-str #{"discovered" long-word})
              :agent-invite (key-generator)
              :spymaster-invite (key-generator)}]
    (sql/insert! ds :game game jdbc/unqualified-snake-kebab-opts))

  (jdbc/execute! ds ["select * from game"] jdbc/unqualified-snake-kebab-opts)

  (def gs (->JdbcGameStore key-generator ds))

  (let [board [{:word "word" :colour :red}
               {:word "discovered" :colour :white}]]
    (def game-id (:id (store/add-game gs board))))

  (def game (store/get-game gs game-id))

  (store/resolve-invite gs (:agent-invite game))

  (store/discover-word gs game-id "discovered")
  )

(defn ->game-store [{::keys [key-generator db-spec]}]
  (let [^HikariDataSource ds (connection/->pool HikariDataSource db-spec)]
    (init-database ds)
    (JdbcGameStore. key-generator ds)))

(defmethod ig/init-key ::provider [_key config]
  (->game-store config))

(defmethod ig/halt-key! ::provider [_key game-store]
  (.close game-store))
