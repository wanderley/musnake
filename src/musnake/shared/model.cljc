(ns musnake.shared.model
  (:require [clojure.test :refer [is deftest testing]]
            [medley.core :refer [map-vals random-uuid]]))

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

(def room-initial-state
  {:snakes {}
   :food (random-pos! board-cols board-rows)})

(def world-initial-state
  (assoc room-initial-state :view 'start-page))

(def universe-initial-state
  {:rooms {:lobby (dissoc world-initial-state :view)}
   :world-rooms {}})

(defn opposite-direction [d]
  (case d
    left  'right
    up    'down
    right 'left
    down  'up
    nil))

(defn change-direction [app-state world-id d]
  (let [room (get-in app-state [:world-rooms world-id])
        op (-> app-state
               (get-in [:rooms room :snakes world-id :direction])
               opposite-direction)]
    (if (= d op)
      app-state
      (assoc-in app-state [:rooms room :snakes world-id :direction] d))))

(defn process-frame [app-state]
  (-> app-state
      (update :rooms
              #(map-vals
                (fn [room]
                  (-> room
                      (update-in [:snakes] update-snakes-alive?)
                      snakes-move-and-eat!
                      revive-dead-snakes!))
                %))))

(defn connect [app-state world-id room unoccupied-pos]
  (-> app-state
      (assoc-in [:rooms room :snakes world-id]
                {:body [unoccupied-pos] :alive? true})
      (assoc-in [:world-rooms world-id] room)))

(defn connect! [app-state room world-id]
  (connect app-state world-id room (get-unoccupied-pos! app-state)))

(defn disconnect [app-state world-id]
  (let [room (get-in app-state [:world-rooms world-id])]
    (-> app-state
        (update-in [:rooms room :snakes] dissoc world-id)
        (update-in [:world-rooms] dissoc world-id))))

(defn new-game [app-state world-id room unoccupied-pos]
  (-> app-state
      (disconnect world-id)
      (assoc-in [:rooms room] room-initial-state)
      (connect world-id room unoccupied-pos)))

(defn new-game! [app-state world-id]
  (new-game app-state
            world-id
            (.toString (random-uuid))
            (get-unoccupied-pos! app-state)))

(deftest test-app-state
  (testing "connect and disconnect"
    (is (= (-> universe-initial-state
               (connect :python :lobby {:x 10 :y 10})
               (get-in [:rooms :lobby :snakes :python :body 0]))
           {:x 10 :y 10}))
    (is (= (-> universe-initial-state
               (connect :python :lobby {:x 10 :y 10})
               (disconnect :python))
           universe-initial-state)))

  (testing "new game"
    (is (= (-> universe-initial-state
               (connect :python :lobby {:x 10 :y 10})
               (new-game :python :private-room {:x 11 :y 11})
               (get-in [:rooms :private-room :snakes :python :body 0]))
           {:x 11 :y 11})))

  (testing "process frame"
    (is (= (-> universe-initial-state
               (connect :python :lobby {:x 10 :y 10})
               (change-direction :python 'up)
               (process-frame)
               (get-in [:rooms :lobby :snakes :python :body 0]))
           {:x 10 :y 9}))))
