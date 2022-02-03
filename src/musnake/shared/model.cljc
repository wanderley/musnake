(ns musnake.shared.model
  (:require [clojure.test :refer [is deftest]]
            [medley.core :refer [map-vals]]))

;;; Model

;;;; Pos

(defn random-pos! [get-occupied-pos x-min x-max y-min y-max]
  (loop []
    (let [x (+ x-min (rand-int (- x-max x-min)))
          y (+ y-min (rand-int (- y-max y-min)))
          pos {:x x :y y}]
      (if (not (contains? get-occupied-pos pos))
        pos (recur)))))

(defn move-pos [pos d]
  {:x (+ (:x pos)
         (case d
           left  -1
           up     0
           right +1
           down   0
           0))
   :y (+ (:y pos)
         (case d
           left   0
           up    -1
           right  0
           down  +1
           0))})

;;;; Board

(defn pos->board-pos [p b]
  {:x (* (:x p) (:cell-size b))
   :y (* (:y p) (:cell-size b))})

(defn inside-board? [b p]
  (and (<= 0 (:x p) (dec (:cols b)))
       (<= 0 (:y p) (dec (:rows b)))))

(defn board-dimensions [b]
  {:width  (* (:cols b) (:cell-size b))
   :height (* (:rows b) (:cell-size b))})

;;;; Snake

(defn snake-head [s]
  (-> s :body first))

(defn grow-snake [s]
  (assoc s :body (into [(-> s snake-head
                            (move-pos (:direction s)))]
                       (:body s))))

(defn move-snake [s]
  (if (:alive? s)
    (assoc s :body
           (into [(-> s snake-head
                      (move-pos (:direction s)))]
                 (-> s :body drop-last)))
    s))

(defn snake-touch-itself? [s]
  (and (> (-> s :body count) 2)
       (some? (some #{(-> s snake-head)}
                    (-> s :body rest)))))

(defn snake-alive? [s b]
  (and (inside-board? b (-> s snake-head))
       (not (snake-touch-itself? s))))

(defn update-snake-alive? [s b]
  (assoc s :alive? (snake-alive? s b)))

(defn snake-ate? [s f]
  (= (get-in s [:body 0]) f))

(defn move-snakes [ms]
  (map-vals #(move-snake %) ms))

(defn update-snakes-alive? [ms b]
  (map-vals #(update-snake-alive? % b) ms))

(deftest test-snake
  (is (= {:body [{:x 10 :y 10} {:x 10 :y 11}] :direction 'up :alive? true}
         (grow-snake {:body [{:x 10 :y 11}] :direction 'up :alive? true})))

  (is (= {:body [{:x 10 :y 10}] :direction 'up :alive? true}
         (move-snake {:body [{:x 10 :y 11}] :direction 'up :alive? true})))

  (is (= false
         (snake-touch-itself?
          {:body [{:x 10 :y 10} {:x 10 :y 11}]
           :direction 'up :alive? true})))
  (is (= true
         (snake-touch-itself?
          {:body [{:x 10 :y 10} {:x 10 :y 10} {:x 10 :y 11}]
           :direction 'up :alive? true})))

  (let [board {:cols 50 :rows 50 :cell-size 10}]
    (is (= true
           (snake-alive? {:body [{:x 10 :y 10}] :direction 'up :alive? true} board)))
    (is (= false
           (snake-alive? {:body [{:x 50 :y 10}] :direction 'up :alive? true} board)))))

;;;; App

(def client-initial-state
  {:snakes {}
   :food  (random-pos! #{} 0 50 0 50)
   :board {:cols 50
           :rows 50
           :cell-size 10}})

(def server-initial-state  client-initial-state)

(defn change-direction [app-state client-id d]
  (assoc-in app-state [:snakes client-id :direction] d))

(defn get-occupied-pos [app-state]
  (->> app-state
       :snakes
       vals
       (map :body)
       flatten
       (concat (-> app-state :food list))
       set))

(defn get-unoccupied-pos! [app-state]
  (random-pos! (get-occupied-pos app-state)
               ;; TODO Remove hardcode values
               0 50 0 50))

(defn snakes-move-and-eat! [app-state]
  (let [{:keys [ate starving]}
        (group-by #(if (and (:alive? (val %))
                            (snake-ate? (val %)
                                        (:food app-state)))
                     :ate :starving)
                  (:snakes app-state))]
    (-> app-state
        (update :food
                #(if (empty? ate) % (get-unoccupied-pos! app-state)))
        (assoc  :snakes
                (merge
                 (map-vals grow-snake (into {} ate))
                 (map-vals move-snake (into {} starving)))))))

(defn revive-dead-snakes! [app-state]
  (update app-state :snakes
          #(map-vals (fn [snake]
                       (if (:alive? snake)
                         snake
                         {:body [(get-unoccupied-pos! app-state)]
                          :alive? true}))
                     %)))

(defn process-frame [app-state]
  (-> app-state
      (update :snakes update-snakes-alive? (:board app-state))
      snakes-move-and-eat!
      revive-dead-snakes!))

(comment
  (-> {:snake {:body [{:x 25 :y 25}] :alive? true}
       :snakes {:python {:body [{:x 8 :y 46}] :alive? true :direction 'up}}
       :food {:x 8 :y 46}
       :board {:cols 50 :rows 50 :cell-size 10}}
      process-frame))

(defn connect! [app-state client-id]
  (assoc-in app-state [:snakes client-id]
            {:body [(get-unoccupied-pos! app-state)]
             :alive? true}))

(defn disconnect [app-state client-id]
  (assoc app-state :snakes (dissoc (:snakes app-state) client-id)))

(deftest test-app-state
  (is (= (get-occupied-pos
          {:snakes {:python {:body [{:x 8 :y 44} {:x 8 :y 45}] :alive? true :direction 'up}
                    :ratlle {:body [{:x 10 :y 10} {:x 11 :y 11}] :alive? true :direction 'up}}
           :food {:x 8 :y 46}
           :board {:cols 50 :rows 50 :cell-size 10}})
         #{{:x 8, :y 45} {:x 8, :y 46} {:x 10, :y 10} {:x 11, :y 11} {:x 8, :y 44}})))
