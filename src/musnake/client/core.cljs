(ns musnake.client.core
  (:require [chord.client :refer [ws-ch]]
            [cljs.core.async :as async :include-macros true]
            [reagent.core :as reagent :refer [atom]]
            [reagent.dom :as rd]))

(enable-console-print!)

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

(def app-state (atom {:snake {:body [{:x 25 :y 25}]
                              :alive? true}
                      :food  (random-pos! #{} 0 50 0 50)
                      :board {:cols 50
                              :rows 50
                              :cell-size 10}
                      :game/settings {:last-frame-timestamp 0
                                      :latency 150}}))

(defn change-direction [app-state d]
  (assoc-in app-state [:snake :body 0 :direction] d))

(defn get-occupied-pos [app-state]
  (set
   (conj
    (->> app-state :snake :body (map extract-pos))
    (-> app-state :food extract-pos))))

(defn maybe-eat! [app-state]
  (if (and (-> app-state :snake :alive?)
           (snake-ate? (-> app-state :snake)
                       (-> app-state :food)))
    (-> app-state
        (update :snake grow)
        (assoc :food (random-pos! (get-occupied-pos app-state)
                                  ;; TODO Remove hardcode values
                                  0 50 0 50)))
    app-state))

(defn toc! [app-state timestamp]
  (let [last-ts (-> app-state :game/settings :last-frame-timestamp)
        latency (-> app-state :game/settings :latency)]
    (if (and timestamp (> (- timestamp last-ts) 150))
      (-> app-state
          (assoc-in [:game/settings :last-frame-timestamp] timestamp)
          (update :snake move-snake)
          (update :snake update-snake-alive? (:board app-state))
          (maybe-eat!))
      app-state)))

;;; Events

(defn emit [fn & params]
  (swap! app-state #(apply fn (concat [%] params))))

;; This is other way to simulate a game loop.  Doing that, we don't need to
;; compute the elapsed time, but just fire tick every X miliseconds.  I am not
;; sure which one is better.
;; (defonce tick (js/setInterval #(emit toc!) (/ 1000 10)))
(defn tick [timestamp]
  (emit toc! timestamp)
  (.requestAnimationFrame js/window tick))
(tick nil)

;;; Views

(defn object [pos color board]
  [:rect (into (pos->board-pos pos board)
               {:width (:cell-size board) :height (:cell-size board)
                :fill color})])

(defn food [food board]
  [object food "green" board])

(defn snake-body [snake board]
  (into
   [:svg]
   (for [o (:body snake)]
     [object o "red" board])))

(defn board [{snake    :snake
              board    :board
              food-pos :food}]
  (let [{:keys [width height]} (board-dimensions board)]
    [:svg {:width  width
           :height height
           :focusable true
           :tabIndex 0
           :ref (fn [el]
                  (when el
                    (.addEventListener
                     el "keydown"
                     (fn [ke]
                       (.preventDefault ke)
                       (when-let [d (case (-> ke .-keyCode)
                                      37 'left
                                      38 'up
                                      39 'right
                                      40 'down
                                      nil)]
                         (emit change-direction d))))))}

     ;; Background
     [:rect {:x 0 :y 0
             :width width
             :height height
             :fill "white"
             :stroke "black"}]

     ;; Objects
     [snake-body snake      board]
     [food       food-pos   board]]))

(defn app []
  [:div {:style {:margin "0"
                 :position "absolute"
                 :top "50%"
                 :left "50%"
                 :-ms-transform "translate(-50%, -50%)"
                 :transform "translate(-50%, -50%)"
                 :border "1px solid black"}}
   [:center
    [:h1 "μSnake"]
    [board @app-state]]
   [:p (str "Direction:"
            (get-in @app-state [:snake :direction])
            " Alive?:"
            (-> @app-state :snake :alive?))]
   [:p (str @app-state)]])

(rd/render [app] (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
