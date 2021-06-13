#!/bin/bash
clojure -M:cljs:fig:min
clojure -X:uberjar
heroku jar:deploy target/kodnevek.jar --app kodnevek
