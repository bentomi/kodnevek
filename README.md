![tests](https://github.com/bentomi/kodnevek/actions/workflows/tests.yml/badge.svg)

# Kódnevek

This is a bare-bones implementation of the board game
[Codenames](https://codenames.game/) (Copyright © 2021 Czech Games Edition).

The implementation just provides enough functionality to play the game when the
cards are not available. It is up to the players to establish a channel for
communication and to observe the rules.

# Dependencies

At least Clojure and Java have to be installed. The software was built with
Clojure 1.10.3 and Java 15.0.2.

# Building

The commands
``` shell
clojure -M:cljs:fig:min
clojure -X:uberjar
```
produce the uberjar `target/kodnevek.jar`

# Running

The generated jar file can be executed by

``` shell
java -jar kodnevek.jar
```

If the environment variable `JDBC_DATABASE_URL` is set, it should point to
PostgreSQL database where the games are stored. If this variable is not set, the
games are not persisted and get lost when the program terminates.

After startup, the application is available at the port specified by the `PORT`
environment variable or at 8080 if `PORT` is not set. For example,
http://localhost:8080/ if no port has been set.

# Testing

## Running Clojure tests

``` shell
clojure -X:test:test-run
```

## Running ClojureScript tests

These tests require Google Chrome to be installed. (The current
setup assumes it is installed at `/opt/google/chrome/chrome`, `test.cljs.edn`
has to be adjusted if it is installed somewhere else.)

The tests can be executed by running
``` shell
clojure -M:cljs:fig:test:test-cljs-run
```

## Test coverage

The test coverage can be checked by running
``` shell
clojure -M:test:cloverage
```

# Development

## Starting a Clojure REPL

``` shell
 clj -A:dev:test
```

## Starting a ClojureScript REPL with Figwheel

``` shell
clj -A:dev:test:cljs:fig:build
```

## Emacs integration with CIDER

I have the following in my `.dir-locals.el` file
``` emacs-lisp
((nil
  (cider-clojure-cli-global-options . "-A:dev:test:cljs:fig")
  (cider-default-cljs-repl . figwheel-main)
  (cider-figwheel-main-default-options . "dev")
  ))
```
and start both a Clojure and a ClojureScript REPL.
