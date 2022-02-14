(ns musnake.shared.model
  (:require [clojure.test :refer [is deftest testing]]
            [medley.core :refer [map-vals]]))

;;; Model

;;;; Pos

(defn random-pos! [cols rows]
  {:x (rand-int cols) :y (rand-int rows)})

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

(def board-cols 50)
(def board-rows 50)
(def board-cell-size 10)

(defn inside-board? [p]
  (and (<= 0 (:x p) (dec board-cols))
       (<= 0 (:y p) (dec board-rows))))

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

(defn snake-alive? [s]
  (and (inside-board? (-> s snake-head))
       (not (snake-touch-itself? s))))

(defn update-snake-alive? [s]
  (assoc s :alive? (snake-alive? s)))

(defn snake-ate? [s f]
  (= (get-in s [:body 0]) f))

(defn move-snakes [ms]
  (map-vals #(move-snake %) ms))

(defn update-snakes-alive? [ms]
  (map-vals #(update-snake-alive? %) ms))

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

  (is (= true
         (snake-alive? {:body [{:x 10 :y 10}] :direction 'up :alive? true})))
  (is (= false
         (snake-alive? {:body [{:x 50 :y 10}] :direction 'up :alive? true}))))

;;;; Room

(defn get-occupied-pos [room]
  (->> room
       :snakes
       vals
       (map :body)
       flatten
       (concat (-> room :food list))
       set))

(defn get-unoccupied-pos! [room]
  (let [occuppied-pos (get-occupied-pos room)]
    (first
     (filter
      #(not (contains? occuppied-pos %))
      (repeatedly #(identity (random-pos! board-cols board-rows)))))))

(defn snakes-move-and-eat! [room]
  (let [{:keys [ate starving]}
        (group-by #(if (and (:alive? (val %))
                            (snake-ate? (val %)
                                        (:food room)))
                     :ate :starving)
                  (:snakes room))]
    (-> room
        (update :food
                #(if (empty? ate) % (get-unoccupied-pos! room)))
        (assoc  :snakes
                (merge
                 (map-vals grow-snake (into {} ate))
                 (map-vals move-snake (into {} starving)))))))

(defn revive-dead-snakes! [room]
  (update room :snakes
          #(map-vals (fn [snake]
                       (if (:alive? snake)
                         snake
                         {:body [(get-unoccupied-pos! room)]
                          :alive? true}))
                     %)))

(deftest test-room
  (testing "occupied positions"
    (is (= (get-occupied-pos
            {:snakes {:python {:body [{:x 8 :y 44} {:x 8 :y 45}] :alive? true :direction 'up}
                      :ratlle {:body [{:x 10 :y 10} {:x 11 :y 11}] :alive? true :direction 'up}}
             :food {:x 8 :y 46}})
           #{{:x 8, :y 45} {:x 8, :y 46} {:x 10, :y 10} {:x 11, :y 11} {:x 8, :y 44}}))))

;;;; App

(def client-initial-state
  {:rooms {:lobby {:snakes {}
                   :food (random-pos! board-cols board-rows)}}})

(def server-initial-state  client-initial-state)

(defn opposite-direction [d]
  (case d
    left  'right
    up    'down
    right 'left
    down  'up
    nil))

(defn change-direction [app-state client-id d]
  (let [op (-> app-state
               (get-in [:rooms :lobby :snakes client-id :direction])
               opposite-direction)]
    (if (= d op)
      app-state
      (assoc-in app-state [:rooms :lobby :snakes client-id :direction] d))))

(defn process-frame [app-state]
  (-> app-state
      (update-in [:rooms :lobby :snakes] update-snakes-alive?)
      (update-in [:rooms :lobby] snakes-move-and-eat!)
      (update-in [:rooms :lobby] revive-dead-snakes!)))

(defn connect [app-state client-id unoccupied-pos]
  (assoc-in app-state [:rooms :lobby :snakes client-id]
            {:body [unoccupied-pos] :alive? true}))

(defn connect! [app-state client-id]
  (connect app-state client-id (get-unoccupied-pos! app-state)))

(defn disconnect [app-state client-id]
  (update-in app-state [:rooms :lobby :snakes] dissoc client-id))

(deftest test-app-state
  (testing "connect and disconnect"
    (is (= (-> client-initial-state
               (connect :python {:x 10 :y 10})
               (get-in [:rooms :lobby :snakes :python :body 0]))
           {:x 10 :y 10}))
    (is (-> client-initial-state
            (connect :python {:x 10 :y 10})
            (disconnect :python)
            (get-in [:rooms :lobby :snakes :python])
            nil?)))

  (testing "process frame"
    (is (= (-> client-initial-state
               (connect :python {:x 10 :y 10})
               (change-direction :python 'up)
               (process-frame)
               (get-in [:rooms :lobby :snakes :python :body 0]))
           {:x 10 :y 9}))))
