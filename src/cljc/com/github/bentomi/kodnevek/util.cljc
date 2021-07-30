(ns com.github.bentomi.kodnevek.util
  (:require [clojure.spec.alpha :as spec]))

(spec/fdef add-to-set
  :args (spec/cat :set (spec/nilable set?) :elem any?)
  :ret set?
  :fn #(contains? (:ret %) (-> % :args :elem)))

(def add-to-set (fnil conj #{}))

(spec/fdef fixed-sample-stream
  :args (spec/cat :n nat-int? :coll seqable?)
  :ret vector?
  :fn #(<= (-> % :ret count) (-> % :args :n)))

(defn fixed-sample-stream
  "Returns a vector of (`min` `n` (`count` `coll`)) elements from `coll`.
  All elements have the same chance of being selected. `count` is not called
  on `coll`, it can be a lazy seq."
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

(spec/fdef random-ints
  :args (spec/cat :n nat-int? :size nat-int?)
  :ret set?
  :fn #(and (= (-> % :ret count)
               (min (-> % :args :n)
                    (-> % :args :size)))
            (every? (fn [e] (< e (-> % :args :size)))
                    (:ret %))))

(defn random-ints
  "Returns a set of (`min` `n` `size`) integers from the range [0, `size`)."
  [n size]
  (let [n (min n size)]
    (loop [res (transient #{})]
      (if (< (count res) n)
        (recur (conj! res (rand-int size)))
        (persistent! res)))))

(spec/fdef fixed-sample-vector
  :args (spec/cat :n nat-int? :coll vector?)
  :ret vector?
  :fn #(= (-> % :ret count)
          (min (-> % :args :n)
               (-> % :args :coll count))))

(defn fixed-sample-vector
  "Returns a vector of (`min` `n` (`count` `coll`)) elements from `coll`.
  All elements have the same chance of being selected."
  [n coll]
  (let [size (count coll)
        n (min n size)
        indices (if (<= n (/ size 2))
                  (random-ints n size)
                  (remove (random-ints (- size n) size) (range size)))]
    (mapv coll indices)))

(spec/fdef fixed-sample
  :args (spec/cat :n nat-int? :coll seqable?)
  :ret vector?
  :fn #(<= (-> % :ret count) (-> % :args :n)))

(defn fixed-sample
  "Returns a vector of (`min` `n` (`count` `coll`)) elements from `coll`.
  All elements have the same chance of being selected. If `coll` is not a
  vector, `count` is not called on it, it can be a lazy seq. If `coll` is
  a vector a more efficient algorithm is used."
  [n coll]
  (if (vector? coll)
    (fixed-sample-vector n coll)
    (fixed-sample-stream n coll)))

(defn assoc-missing
  "Associates a key with a value in a map, if and only if the key is not
  already mapped to non-nil value."
  ([m k v]
   (if (some? (get m k)) m (assoc m k v)))
  ([m k v & kvs]
   (reduce (fn [m [k v]] (assoc-missing m k v))
           (assoc-missing m k v)
           (partition 2 kvs))))

#?(:clj
   (spec/fdef with-send-off-executor
     :args (spec/cat :binding (spec/spec (spec/cat :name simple-symbol?
                                                   :executor any?))
                     :body (spec/+ any?))))

#?(:clj
   (defmacro with-send-off-executor
     "Creates an ExecutorService by calling `executor`, sets it for the scope
  of the form as executor for `send-off`, `future`, etc. and executes `body`.
  The executor service created is shut down after the execution of `body`."
     [[name executor] & body]
     `(let [~name ~executor
            original-executor# clojure.lang.Agent/soloExecutor]
        (set-agent-send-off-executor! ~name)
        (try
          ~@body
          (finally
            (set-agent-send-off-executor! original-executor#)
            (.shutdown ~name))))))
