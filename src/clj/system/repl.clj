(ns system.repl
  (:require [game.output :refer [output-with-fn]]
            [system.game :refer [get-game-infos]]
            [system.websockets :refer [send-infos]]))

(defn output-to-websockets [play websockets]
  (output-with-fn play
                  (fn [game-infos]
                    (println game-infos)
                    (send-infos websockets :sente/all-users-without-uid (get-game-infos game-infos)))))