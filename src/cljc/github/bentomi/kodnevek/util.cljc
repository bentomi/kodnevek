(ns github.bentomi.kodnevek.util)

(def add-to-set (fnil conj #{}))

(defn fixed-sample
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
