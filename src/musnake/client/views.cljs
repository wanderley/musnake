(ns musnake.client.views
  (:require [medley.core :refer [abs]]
            [reagent.core :as reagent :refer [atom]]
            [musnake.shared.model :as m]
            [devcards.core :refer [defcard defcard-rg]]))

(enable-console-print!)

(defcard
  "# Board")

(defn object [pos color board]
  [:rect (into (m/pos->board-pos pos board)
               {:width (:cell-size board) :height (:cell-size board)
                :fill color})])

(defn food [food board]
  [object food "green" board])

(defn snake-body [snake color board]
  (into
   [:svg]
   (for [o (:body snake)]
     [object o color board])))

(defn touch-event->pos [te]
  {:x (-> te .-targetTouches last .-pageX)
   :y (-> te .-targetTouches last .-pageY)})

(defn touch-move->direction [from to]
  (let [{x1 :x y1 :y} from
        {x2 :x y2 :y} to
        dx (- x2 x1)
        dy (- y2 y1)
        udx (abs dx)
        udy (abs dy)]
    (cond
      (< udx udy) (if (neg? dy) 'up 'down)
      (< udy udx) (if (neg? dx) 'left 'right)
      :else       nil)))

(defn board [& _]
  (let [last-touch (atom nil)
        last-key   (atom nil)
        svg-ref-with-listener (atom nil)]
    (fn [{client-id :client-id
          snakes    :snakes
          board     :board
          food-pos  :food}
         on-change-direction]
      [:div {:style {:width     "90%"
                     :max-width "500px"}}
       [:div {:style {:padding   "1em"}}
        (into [:svg {:width  "100%"
                     :height "100%"
                     :viewBox "0 0 500 500"
                     :preserveAspectRatio "xMidYMid meet"
                     :focusable "true"
                     :tabIndex 0
                     :background "lightyellow"
                     :ref (fn [el]
                            (when (and el (not (= el @svg-ref-with-listener)))
                              (.addEventListener
                               el "keydown"
                               (fn [ke]
                                 (.preventDefault ke)
                                 (println "keydown" ke)
                                 (when-let [d (case (-> ke .-keyCode)
                                                37 'left
                                                38 'up
                                                39 'right
                                                40 'down
                                                nil)]
                                   (on-change-direction d))))
                              (.addEventListener
                               el "touchstart"
                               (fn [from]
                                 (.preventDefault from)
                                 (let [curr-touch (touch-event->pos from)]
                                   (reset! last-touch curr-touch))))
                              (.addEventListener
                               el "touchmove"
                               (fn [to]
                                 (.preventDefault to)
                                 (let [curr-touch (touch-event->pos to)]
                                   (when-let [d (touch-move->direction
                                                 (or @last-touch curr-touch) curr-touch)]
                                     (on-change-direction d)))))
                              (reset! svg-ref-with-listener el)))}

               ;; Background
               [:rect {:x 0 :y 0
                       :width "100%"
                       :height "100%"
                       :fill "lightyellow"}]

               ;; Objects
               [food food-pos board]]
              (for [[id snake] snakes]
                [snake-body snake (if (= id client-id)
                                    "blue" "red")
                 board]))]])))

(defcard empty-board
  "An empty board with just one food rendered on it.  Note that a valid board
  always has at least one food placed on it."
  (reagent/as-element
   [board m/client-initial-state #()]))

(defcard-rg user-can-move-around
  (fn [app-state _]
    [board @app-state
     (fn [d]
       (swap! app-state
              #(-> %
                   (m/change-direction :test d)
                   (m/process-frame))))])
  (atom (-> m/client-initial-state
            (assoc :client-id :test)
            (assoc-in [:snakes :test]
                      {:body [{:x 10 :y 10}] :direction 'up :alive? true})))
  {:inspect-data true})

(defcard "# Game Selection")

(defn game-screen [& children]
  [:div {:style {:width            "500px"
                 :height           "500px"
                 :background-color "lightyellow"}}
   children])

(defn vertical-centered [& children]
  [:div {:style {:width  "100%"
                 :height "100%"}}
   [:div {:style {:margin        "0"
                  :position      "relative"
                  :top           "50%"
                  :left          "50%"
                  :width         "100%"
                  :-ms-transform "translate(-50%, -50%)"
                  :transform     "translate(-50%, -50%)"}}
    children]])

(defn menu-option [value on-click]
  [:div {:style {:display         "flex"
                 :padding-top     "0.5em"
                 :padding-bottom  "0.5em"
                 :justify-content "center"}}
   [:button {:style {:width            "80%"
                     :padding          "1em"
                     :font-size        "1em"
                     :background-color "lightgreen"}
             :on-click #(on-click)}
    value]])


(defn start-page []
  [game-screen
   [vertical-centered
    [menu-option [:strong "Play Now"] #(js/alert "Play Now")]
    [menu-option "New Game" #(js/alert "New Game")]
    [menu-option "Join Game" #(js/alert "Join Game")]]])

(defcard-rg start-page
  [start-page])

(defn menu [& children]
  [:div {:style {:display         "flex"
                 :flex-direction  "column"
                 :align-items     "strech"
                 :text-align      "center"
                 :justify-content "center"
                 :height          "100%"}}
   children])

(defn menu-item [& children]
  [:div {:style {:margin-top    "1em"
                 :margin-bottom "1em"}}
   children])

(defn menu-copypasta-item [value]
  [menu-item
     [:input {:type  "input"
              :value value
              :style {:box-sizing       "border-box"
                      :width            "80%"
                      :padding          "1em"
                      :font-size        "1em"
                      :background-color "lightgreen"}}]])

(defn menu-button [value on-click]
  [menu-item
   [:button {:style {:width            "80%"
                     :padding          "1em"
                     :font-size        "1em"
                     :background-color "lightgreen"}
             :on-click on-click}
    value]])

(defn new-game-page [url on-click]
  [game-screen
   [menu
    [menu-copypasta-item url]
    [menu-button "Play Now" on-click]]])

(defcard-rg new-game-page
  [new-game-page
   "https://musnake.herokuapp.com/game/XXXX-XXXX"
   #(js/alert "Play Now")])
