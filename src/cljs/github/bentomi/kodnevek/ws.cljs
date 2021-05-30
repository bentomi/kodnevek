(ns github.bentomi.kodnevek.ws
  (:require [re-frame.core :as re-frame]))

(defn connect [url]
  (let [w (js/WebSocket. url)]
    (set! (.-onmessage w) #(js/console.log %))
    (set! (.-onclose w) #(js/console.log %))
    w))
