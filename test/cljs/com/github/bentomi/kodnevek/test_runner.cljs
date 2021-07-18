(ns com.github.bentomi.kodnevek.test-runner
  (:require [cljs.test :refer-macros [run-tests]]
            [cljs-test-display.core]
            [com.github.bentomi.kodnevek.game-test]))

(defn -main [& args]
  (run-tests 'com.github.bentomi.kodnevek.game-test))
