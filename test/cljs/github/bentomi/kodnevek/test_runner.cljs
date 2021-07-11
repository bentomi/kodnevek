(ns github.bentomi.kodnevek.test-runner
  (:require [cljs.test :refer-macros [run-tests]]
            [cljs-test-display.core]
            [github.bentomi.kodnevek.game-test]))

(defn -main [& args]
  (run-tests 'github.bentomi.kodnevek.game-test))
