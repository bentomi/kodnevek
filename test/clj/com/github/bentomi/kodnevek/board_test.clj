(ns com.github.bentomi.kodnevek.board-test
  (:require [clojure.string :as str]
            [clojure.test :as test]
            [clojure.spec.alpha :as spec]
            [clojure.spec.test.alpha :as stest]
            [com.github.bentomi.kodnevek.board :as b]))

(defn report-results [check-results]
  (let [checks-passed? (->> check-results (map :failure) (every? nil?))]
    (if checks-passed?
      (test/do-report {:type :pass
                       :message (str "Generative tests pass for "
                                     (str/join ", " (map :sym check-results)))})
      (doseq [failed-check (filter :failure check-results)]
        (let [r (stest/abbrev-result failed-check)
              failure (:failure r)]
          (test/do-report
           {:type :fail
            :message (with-out-str (spec/explain-out failure))
            :expected (->> r :spec rest (apply hash-map) :ret)
            :actual (if (instance? Throwable failure)
                      failure
                      (::stest/val failure))}))))
    checks-passed?))

(defmacro defspec-test
  ([name sym-or-syms] `(defspec-test ~name ~sym-or-syms nil))
  ([name sym-or-syms opts]
   (when test/*load-tests*
     `(defn ~(vary-meta name assoc :test
              `(fn [] (report-results (stest/check ~sym-or-syms ~opts))))
        [] (test/test-var (var ~name))))))

(defspec-test auto-tests
  (stest/enumerate-namespace 'com.github.bentomi.kodnevek.board))
