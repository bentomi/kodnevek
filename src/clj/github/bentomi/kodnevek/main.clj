(ns github.bentomi.kodnevek.main
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [integrant.core :as ig]
            [github.bentomi.kodnevek.game :as game]
            [github.bentomi.kodnevek.in-mem-game-store :as game-store]
            [github.bentomi.kodnevek.server :as server]
            [github.bentomi.kodnevek.words :as words]))

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
                         ::words/provider (ig/ref ::words/provider)}
     ::game/provider {::game/word-provider (ig/ref ::words/provider)
                      ::game/game-store (ig/ref ::game-store/provider)}
     ::words/provider {::words/resources {"en" "cn-words-en.txt"
                                          "ru" "cn-words-ru.txt"}}
     ::game-store/provider {::game-store/key-generator key-generator}}))

(defn -main [& args]
  (let [seed (bit-xor (System/currentTimeMillis) (System/nanoTime))
        sys (ig/init (system seed))]
    (.addShutdownHook (Runtime/getRuntime) (Thread. #(ig/halt! sys) "Cleaner"))
    (log/info "System started")))
