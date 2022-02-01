(ns musnake.shared.model)

;;; Model

;;;; Pos

(defn random-pos! [get-occupied-pos x-min x-max y-min y-max]
  (loop []
    (let [x (+ x-min (rand-int (- x-max x-min)))
          y (+ y-min (rand-int (- y-max y-min)))
          pos {:x x :y y}]
      (if (not (contains? get-occupied-pos pos))
        pos (recur)))))

(defn extract-pos [something]
  (select-keys something [:x :y]))

(defn pos=? [a b]
  (= (extract-pos a) (extract-pos b)))

;;;; Object

(defn move-object [pos]
  {:x (+ (:x pos)
         (case (:direction pos)
           left  -1
           up     0
           right +1
           down   0
           0))
   :y (+ (:y pos)
         (case (:direction pos)
           left   0
           up    -1
           right  0
           down  +1
           0))
   :direction (:direction pos)})

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

(defn grow [s]
  (assoc s :body (into [(-> s snake-head move-object)]
                       (:body s))))

(defn move-snake [s]
  (if (:alive? s)
    (assoc s :body
           (into [(-> s snake-head move-object)]
                 (-> s :body drop-last)))
    s))

(defn update-snake-alive? [s b]
  (update s :alive?
          #(and %
                (inside-board? b (-> s snake-head))
                (or (= 1 (-> s :body count))
                    (nil?
                     (some #{(-> s snake-head extract-pos)}
                           (->> s :body rest (map extract-pos))))))))

(defn snake-ate? [s f]
  (pos=? (get-in s [:body 0]) f))

;;;; App

(def client-initial-state
  {:client {}
   :snake {:body [{:x 25 :y 25}]
           :alive? true}
   :food  (random-pos! #{} 0 50 0 50)
   :board {:cols 50
           :rows 50
           :cell-size 10}})

(def server-initial-state  client-initial-state)

(defn change-direction [app-state d]
  (assoc-in app-state [:snake :body 0 :direction] d))

(defn get-occupied-pos [app-state]
  (set
   (conj
    (->> app-state :snake :body (map extract-pos))
    (-> app-state :food extract-pos))))

(defn get-unoccupied-pos! [app-state]
  (random-pos! (get-occupied-pos app-state)
               ;; TODO Remove hardcode values
               0 50 0 50))

(defn maybe-eat! [app-state]
  (if (and (-> app-state :snake :alive?)
           (snake-ate? (-> app-state :snake)
                       (-> app-state :food)))
    (-> app-state
        (update :snake grow)
        (#(assoc % :food (get-unoccupied-pos! %))))
    app-state))

(defn process-frame [app-state]
  (-> app-state
      (update :snake move-snake)
      (update :snake update-snake-alive? (:board app-state))
      (maybe-eat!)))

(defn connect! [app-state client-id]
  (assoc-in app-state [:client client-id]
            {:body [{:x 25 :y 25}]
             :alive? true})
  app-state)

(defn disconnect [app-state client-id]
  app-state)
