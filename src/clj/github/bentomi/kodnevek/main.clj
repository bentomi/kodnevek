(ns github.bentomi.kodnevek.main
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [github.bentomi.kodnevek.game :as game]
            [github.bentomi.kodnevek.server :as server]
            [github.bentomi.kodnevek.words :as words]))

(defn system []
  {::server/container {:io.pedestal.http/type :jetty
                       :io.pedestal.http/join? false
                       :io.pedestal.http/port 8080
                       :io.pedestal.http/host "0.0.0.0"
                       :io.pedestal.http/resource-path "public"
                       ::server/main-script "js/main.js"
                       ::game/provider (ig/ref ::game/provider)
                       ::words/provider (ig/ref ::words/provider)}
   ::game/provider {::words/provider (ig/ref ::words/provider)}
   ::words/provider {::words/resources {"en" "cn-words-en.txt"
                                        "ru" "cn-words-ru.txt"}}})

(defn -main [& args]
  (let [sys (ig/init (system))]
    (.addShutdownHook (Runtime/getRuntime) (Thread. #(ig/halt! sys) "Cleaner"))
    (log/info "System started")))
