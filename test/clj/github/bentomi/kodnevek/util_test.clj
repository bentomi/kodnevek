(ns github.bentomi.kodnevek.util-test
  (:require [clojure.test :as test :refer [deftest is]]
            [clojure.spec.test.alpha :as stest]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [github.bentomi.kodnevek.util :as u]))

(stest/check (stest/enumerate-namespace 'github.bentomi.kodnevek.util))

(deftest sample-elements-and-sizes
  (checking "the right number of members of the population" 10
   [n gen/nat
    population (gen/set gen/any-equatable {:max-elements 1000})
    wrapper-fn (gen/elements [vec seq])]
   (let [population-size (count population)
         sample-size (min n population-size)
         s (u/fixed-sample n (wrapper-fn population))]
     (is (= sample-size (count s)))
     (is (every? #(contains? population %) s)))))

(def ^:private allowed-deviation (/ 3 100))

(deftest samples-are-uniform
  (checking "samples have a uniform distribution" 3
   [n (gen/fmap inc gen/nat)
    population (gen/vector-distinct gen/small-integer
                                    {:min-elements 1 :max-elements 100})
    wrapper-fn (gen/elements [identity seq])]
   (let [selections 1000000
         population-size (count population)
         sample-size (min n population-size)
         expected-occurrence (* (/ selections population-size) sample-size)
         max-deviation (* expected-occurrence allowed-deviation)
         min-occurrence (- expected-occurrence max-deviation)
         max-occurrence (+ expected-occurrence max-deviation)]
     (->> (repeatedly selections #(u/fixed-sample n (wrapper-fn population)))
          (apply concat)
          frequencies
          (every? #(<= min-occurrence (second %) max-occurrence))))))
