(ns user
  (:require [clojure.java.io :as io]
            [clojure.tools.namespace.repl :refer [set-refresh-dirs]]
            [clojure.java.classpath :refer [classpath-directories]]
            [integrant.repl :refer [clear go halt prep init reset reset-all]]
            [github.bentomi.kodnevek.main :as main]))

(apply set-refresh-dirs
       (remove #(= (.getName %) "target") (classpath-directories)))

(io/make-parents "target/public/f")

(integrant.repl/set-prep!
 #(-> (main/system 0)
      (assoc-in [:github.bentomi.kodnevek.server/container
                 :io.pedestal.http/file-path]
                "target/public")
      (assoc-in [:github.bentomi.kodnevek.server/container
                 :github.bentomi.kodnevek.server/main-script]
                "cljs-out/dev-main.js")
      (assoc-in [:github.bentomi.kodnevek.server/container
                 :io.pedestal.http/secure-headers
                 :content-security-policy-settings]
                "")))
