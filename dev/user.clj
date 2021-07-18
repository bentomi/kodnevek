(ns user
  (:require [clojure.java.io :as io]
            [clojure.tools.namespace.repl :refer [set-refresh-dirs]]
            [clojure.java.classpath :refer [classpath-directories]]
            [integrant.repl :refer [clear go halt prep init reset reset-all]]
            [com.github.bentomi.kodnevek.main :as main]))

(apply set-refresh-dirs
       (remove #(= (.getName %) "target") (classpath-directories)))

(io/make-parents "target/public/f")

(integrant.repl/set-prep!
 #(-> (main/system 0)
      (assoc-in [:com.github.bentomi.kodnevek.server/container
                 :io.pedestal.http/file-path]
                "target/public")
      (assoc-in [:com.github.bentomi.kodnevek.server/container
                 :com.github.bentomi.kodnevek.server/main-script]
                "cljs-out/dev-main.js")
      (assoc-in [:com.github.bentomi.kodnevek.server/container
                 :io.pedestal.http/secure-headers
                 :content-security-policy-settings]
                "")))
