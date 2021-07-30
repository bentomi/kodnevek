(ns com.github.bentomi.kodnevek.main
  "The entry point of the backend part. It is responsible for the wiring and
  life cycle handling of the components."
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [integrant.core :as ig]
            [com.github.bentomi.kodnevek.game :as game]
            [com.github.bentomi.kodnevek.game-store :as game-store]
            [com.github.bentomi.kodnevek.in-mem-game-store :as mem-game-store]
            [com.github.bentomi.kodnevek.jdbc-game-store :as jdbc-game-store]
            [com.github.bentomi.kodnevek.server :as server]
            [com.github.bentomi.kodnevek.in-mem-word-provider :as word-provider]
            [com.github.bentomi.kodnevek.ws-handler :as ws-handler]))

(derive ::jdbc-game-store/provider ::game-store/provider)
(derive ::mem-game-store/provider ::game-store/provider)

(defn- coerce-to-int [property]
  (if (int? property)
    property
    (Long/parseLong property)))

(defn- game-store-conf [key-generator]
  (if-let [jdbc-url (env :jdbc-database-url)]
    {::jdbc-game-store/provider {::jdbc-game-store/key-generator key-generator
                                 ::jdbc-game-store/db-spec {:jdbcUrl jdbc-url}}}
    {::mem-game-store/provider {::mem-game-store/key-generator key-generator}}))

(defn system [seed]
  (let [rng (java.util.Random. seed)
        key-generator #(-> rng .nextInt Math/abs (Integer/toString 36))]
    (merge
     {::server/container
      {:io.pedestal.http/type :jetty
       :io.pedestal.http/join? false
       :io.pedestal.http/port (coerce-to-int (env :port 8080))
       :io.pedestal.http/host "0.0.0.0"
       :io.pedestal.http/resource-path "public"
       ::server/main-script "js/main.js"
       ::server/event-handler (ig/ref ::game/provider)
       ::server/word-provider (ig/ref ::word-provider/provider)
       ::server/ws-handler (ig/ref ::ws-handler/provider)}
      ::game/provider
      {::game/word-provider (ig/ref ::word-provider/provider)
       ::game/game-store (ig/ref ::game-store/provider)
       ::game/ws-sender (ig/ref ::ws-handler/provider)}
      ::word-provider/provider
      {::word-provider/resources
       {"en" "cn-words-en.txt"
        "ru" "cn-words-ru.txt"
        "de" "cn-words-de.txt"}}
      ::ws-handler/provider
      {}}
     (game-store-conf key-generator))))

(defn -main [& _args]
  (let [seed (bit-xor (System/currentTimeMillis) (System/nanoTime))
        sys (ig/init (system seed))]
    (.addShutdownHook (Runtime/getRuntime) (Thread. #(ig/halt! sys) "Cleaner"))
    (log/info "System started")))
