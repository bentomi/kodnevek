(ns github.bentomi.kodnevek.words
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [medley.core :as m]))

(defn- read-lines [resource]
  (when-let [res (io/resource resource)]
    (with-open [r (io/reader res)]
      (vec (line-seq r)))))

(defn- fixed-sample
  "Returns a vector of at most `n` elements from `coll`. All elements
  have the same chance of being selected."
  [n coll]
  (loop [i 0, s (seq coll), res (transient [])]
    (if s
      (let [i1 (inc i), e (first s)]
        (recur i1 (next s) (if (< i n)
                             (conj! res e)
                             (let [p (rand-int i1)]
                               (if (< p n)
                                 (assoc! res p e)
                                 res)))))
      (persistent! res))))

(comment
  (fixed-sample 5 (range 10))
  (->> (repeatedly 100000 #(fixed-sample 17 (range 100)))
       (apply concat)
       frequencies
       sort
       (run! (comp println second)))
  )

(defprotocol WordProvider
  (get-languages [this]
    "Returns the set of supported languages.")
  (get-words [this lang size]
    "Returns a list of `size` words for language `lang`."))

(deftype InMemoryWordProvider [word-lists]
  WordProvider
  (get-languages [this] (set (keys word-lists)))
  (get-words [this lang size] (when-let [words (get word-lists lang)]
                                (shuffle (fixed-sample size words)))))

(defmethod ig/init-key ::provider [_key config]
  (InMemoryWordProvider. (m/map-vals read-lines (::resources config))))
