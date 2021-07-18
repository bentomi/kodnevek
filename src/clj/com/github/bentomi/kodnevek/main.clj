(ns com.github.bentomi.kodnevek.main
  "The entry point of the backend part. It is responsible for the wiring and
  life cycle handling of the components."
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [integrant.core :as ig]
            [com.github.bentomi.kodnevek.game :as game]
            [com.github.bentomi.kodnevek.in-mem-game-store :as game-store]
            [com.github.bentomi.kodnevek.server :as server]
            [com.github.bentomi.kodnevek.in-mem-word-provider :as word-provider]
            [com.github.bentomi.kodnevek.ws-handler :as ws-handler]))

(defn- coerce-to-int [property]
  (if (int? property)
    property
    (Long/parseLong property)))

(defn system [seed]
  (let [rng (java.util.Random. seed)
        key-generator #(-> rng .nextInt Math/abs (Integer/toString 36))]
    {::server/container {:io.pedestal.http/type :jetty
                         :io.pedestal.http/join? false
                         :io.pedestal.http/port (coerce-to-int (env :port 8080))
                         :io.pedestal.http/host "0.0.0.0"
                         :io.pedestal.http/resource-path "public"
                         ::server/main-script "js/main.js"
                         ::server/event-handler (ig/ref ::game/provider)
                         ::server/word-provider (ig/ref ::word-provider/provider)
                         ::server/ws-handler (ig/ref ::ws-handler/provider)}
     ::game/provider {::game/word-provider (ig/ref ::word-provider/provider)
                      ::game/game-store (ig/ref ::game-store/provider)
                      ::game/ws-sender (ig/ref ::ws-handler/provider)}
     ::word-provider/provider {::word-provider/resources
                               {"en" "cn-words-en.txt"
                                "ru" "cn-words-ru.txt"}}
     ::game-store/provider {::game-store/key-generator key-generator}
     ::ws-handler/provider {}}))

(defn -main [& args]
  (let [seed (bit-xor (System/currentTimeMillis) (System/nanoTime))
        sys (ig/init (system seed))]
    (.addShutdownHook (Runtime/getRuntime) (Thread. #(ig/halt! sys) "Cleaner"))
    (log/info "System started")))
