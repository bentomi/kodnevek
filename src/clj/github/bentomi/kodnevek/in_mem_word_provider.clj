(ns github.bentomi.kodnevek.in-mem-word-provider
  "An implementation of the word provider interface reading words from classpath
  resources and keeping them in memory."
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [medley.core :as m]
            [github.bentomi.kodnevek.util :as util]
            [github.bentomi.kodnevek.words :as words]))

(defn- read-lines [resource]
  (when-let [res (io/resource resource)]
    (with-open [r (io/reader res)]
      (vec (line-seq r)))))

(deftype InMemoryWordProvider [word-lists]
  words/WordProvider
  (get-languages [this]
    (set (keys word-lists)))
  (get-words [this lang size]
    (when-let [words (get word-lists lang)]
      (shuffle (util/fixed-sample size words)))))

(defn ->word-provider [resource-map]
  (->InMemoryWordProvider (m/map-vals read-lines resource-map)))

(defmethod ig/init-key ::provider [_key config]
  (->word-provider (::resources config)))
