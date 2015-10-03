(ns strategy.negamax
  (:require [game.game :refer :all]))

(def negamax-table (atom {}))

(defn negamax-tt-lookup [board]
  {:pre [(some? board)]}
  (if-let [entry (find @negamax-table board)]
    (val entry) nil))

(defn negamax-tt-save! [board score depth type]
  {:pre [(some? board)(some? score)(some? depth)(some? type)]}
  (let [saved-negamax-value (negamax-tt-lookup board)]
    (when (or (nil? saved-negamax-value)
              (> depth (:depth saved-negamax-value)))
      (swap! negamax-table assoc board {:depth depth :score score :type type}))))

(defn merge-and-add-stats [stats next-stats]
  (let [calculated-moves (+ (:calculated-moves stats) (:calculated-moves next-stats))
        lookup-moves     (+ (:lookup-moves stats) (:lookup-moves next-stats))
        considered-moves (+ (:considered-moves stats) (:considered-moves next-stats))]
    {:calculated-moves calculated-moves
     :lookup-moves     lookup-moves
     :total-moves      (+ calculated-moves lookup-moves)
     :considered-moves considered-moves}))

;{:game-position next-game-position :move move}
(defn order-game-positions [game-positions]
  (let [result (sort-by (fn [{{:keys [board player]} :game-position move :move}]
                          (if-let [e (negamax-tt-lookup board)]
                            (cond (= (:type e) :exact) (- (+ 1000 (:score e)))
                                  ;(= (:type e) :lowerbound) (- (+ 500 (:score e)))
                                  :else 1000)
                            1000)) game-positions)]
    result))

(defn negamax-tt-lookup-with-depth [board depth]
  (when-let [saved-negamax-value (negamax-tt-lookup board)]
    (when (>= (:depth saved-negamax-value) depth) saved-negamax-value)))

(defn negamax-tt-save-with-bounds! [board score depth alpha beta]
  (let [type (cond (<= score alpha) :lowerbound
                   (>= score beta)  :upperbound
                   :else            :exact)]
    (negamax-tt-save! board score depth type)))

(defn next-alpha-beta [saved-negamax-value alpha-beta type]
  (if (nil? saved-negamax-value)
    alpha-beta
    (let [comp-fn     (case type :lowerbound > :upperbound <)
          saved-score (:score saved-negamax-value)]
      (if (and (= type (:type saved-negamax-value))
               (comp-fn saved-score alpha-beta))
        saved-score alpha-beta))))

(declare negamax-choose-move)

(defn negamax-step [{:keys [alpha beta best-negamax-values best-game-position best-move best-principal-variation stats]} 
                    {next-game-position :game-position, next-move :move} depth score-fun]
  (let [{next-negamax-values      :negamax-values
         next-stats               :stats
         next-principal-variation :principal-variation}     
                                (negamax-choose-move next-game-position (- beta) (- alpha) (- depth 1) score-fun)
        next-negamax-values     (assoc next-negamax-values :best-possible-score (- (:best-possible-score next-negamax-values)))
        next-best-game-position (if (> (:best-possible-score next-negamax-values) (:best-possible-score best-negamax-values)) 
                                  {:game-position next-game-position   :move next-move 
                                   :negamax-values next-negamax-values :principal-variation next-principal-variation}
                                  {:game-position best-game-position   :move best-move 
                                   :negamax-values best-negamax-values :principal-variation best-principal-variation})
        next-alpha              (max alpha (:best-possible-score next-negamax-values))]
    (let [result {:alpha                    next-alpha
                  :beta                     beta
                  :best-game-position       (:game-position next-best-game-position)
                  :best-move                (:move next-best-game-position)
                  :best-negamax-values      (:negamax-values next-best-game-position)
                  :best-principal-variation (:principal-variation next-best-game-position)
                  :stats                    (merge-and-add-stats stats next-stats)}]
      (if (>= next-alpha beta) (reduced result) result))))

(defn negamax-choose-move
  ([{:keys [board outcome] :as game-position} alpha beta depth score-fun]
   "For a game, applies the negamax algorithm on the tree up to depth,
   returns an object with a :next-move value and :next-game-position that 
   returns the next best game-position from which the value was calculated."
   ; first we retrieve the move from the transposition table
   (let [saved-negamax-value (negamax-tt-lookup-with-depth board depth)
         next-alpha          (next-alpha-beta saved-negamax-value alpha :lowerbound)
         next-beta           (next-alpha-beta saved-negamax-value beta :upperbound)]
     (if (and (some? saved-negamax-value)
              (or 
                ; we have an exact match
                (= :exact (:type saved-negamax-value))
                ; lowerbound is bigger than upperbound
                (>= next-alpha next-beta)))
       
       ; we found a match and return
       {:negamax-values {:best-possible-score (:score saved-negamax-value)}
        :stats          {:calculated-moves 0 :lookup-moves 1 :considered-moves 0}}
       
       ; we go on
       (if (or outcome (= depth 0))
         (let [score (score-fun game-position)]
           ; we save that in the transposition table
           (negamax-tt-save-with-bounds! board score depth next-alpha next-beta)
           {:negamax-values {:best-possible-score score
                             :outcome outcome}
            :stats          {:calculated-moves 1 :lookup-moves 0 :considered-moves 0}})
         ; else we go on with negamax checking all the moves
         (let [
               next-games                 (order-game-positions (next-game-positions game-position))
               ;snext-games                 (next-game-positions game-position)
               negamax-best-game-position (reduce #(negamax-step %1 %2 depth score-fun)
                                                  {:alpha next-alpha 
                                                   :beta next-beta 
                                                   :best-negamax-values {:best-possible-score -1000} 
                                                   :stats {:calculated-moves 0 :lookup-moves 0 :considered-moves (count next-games)}} next-games)
               negamax-values             (:best-negamax-values negamax-best-game-position)
               game-position-with-score   {:principal-variation (cons (:best-game-position negamax-best-game-position) 
                                                                      (:best-principal-variation negamax-best-game-position))
                                           :next-game-position  (:best-game-position  negamax-best-game-position)
                                           :next-move           (:best-move           negamax-best-game-position)
                                           :negamax-values      negamax-values
                                           :stats               (:stats               negamax-best-game-position)}]
           (negamax-tt-save-with-bounds! board (:best-possible-score negamax-values) 
                                         depth 
                                         (:alpha negamax-best-game-position) 
                                         (:beta negamax-best-game-position))
           game-position-with-score)))))
  ([game-position depth score-fun]
   (negamax-choose-move game-position -1000 1000 depth score-fun)))

(defrecord NegamaxStrategy [score-fun depth]
  Strategy
  (choose-next-move [this game-position] 
                    (reset! negamax-table {})
                    (reduce (fn [step-result current-depth]
                              (let [start-time    (System/nanoTime)
                                    result        (negamax-choose-move game-position current-depth score-fun)
                                    end-time      (System/nanoTime)
                                    time-at-depth (double (/ (- end-time start-time) 1000000))]
                                
                                (-> step-result
                                    (merge result)
                                    (dissoc :negamax-values)
                                    (assoc :additional-infos 
                                      (conj (:additional-infos step-result)  
                                            {:depth          current-depth
                                             :negamax-values (:negamax-values result)
                                             :time           time-at-depth
                                             :moves-per-ms   (double (/ (:total-moves (:stats result)) time-at-depth))
                                             :stats          (:stats result)})))))
                            {:additional-infos []} 
                            (range 1 (+ 1 depth))
                            ; [depth]
                            )))

(defn negamax [score-fun depth]
  (->NegamaxStrategy score-fun depth))
