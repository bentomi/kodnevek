(ns github.bentomi.kodnevek.global-specs
  (:require [clojure.spec.alpha :as s]))

(s/def ::id string?)
(s/def ::invite string?)
(s/def ::agent-invite ::invite)
(s/def ::spymaster-invite ::invite)

(s/def ::word string?)
(s/def ::colour #{:red :blue :white :black :clear})
(s/def ::index nat-int?)
(s/def ::field (s/keys :req-un [::word] :opt-un [::colour ::index]))
(s/def ::board (s/and (s/coll-of ::field :kind vector?)
                      #(= (count %)
                          (count (into #{} (map :word) %)))))
(s/def ::discovered-codes (s/coll-of ::word :kind set?))

(s/def ::game
  (s/keys :req-un [::board]
          :opt-un [::id ::agent-invite ::spymaster-invite ::discovered-codes]))

(s/def ::role #{:agent :spymaster})
