(ns github.bentomi.kodnevek.in-mem-word-provider-test
  (:require [clojure.test :as test :refer [deftest is]]
            [github.bentomi.kodnevek.words :as words]
            [github.bentomi.kodnevek.in-mem-word-provider :as wp]))

(deftest interface-test
  (let [resource-map {"en" "cn-words-en.txt"
                      "ru" "cn-words-ru.txt"}
        wp (wp/->word-provider resource-map)]
    (is (= (set (keys resource-map))
           (words/get-languages wp)))
    (doseq [lang (keys resource-map)]
      (let [words (words/get-words wp lang 25)]
        (is (vector? words))
        (is (every? string? words))
        (is (= (count words) (count (set words))))))))
