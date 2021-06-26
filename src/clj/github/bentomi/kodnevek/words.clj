(ns github.bentomi.kodnevek.words
  "The interface for providing words for the game in different languages.")

(defprotocol WordProvider
  (get-languages [this]
    "Returns the set of supported languages.")
  (get-words [this lang size]
    "Returns a list of `size` words for language `lang`."))
