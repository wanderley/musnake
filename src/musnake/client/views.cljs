(ns musnake.client.views
  (:require [medley.core :refer [abs]]
            [reagent.core :as reagent :refer [atom]]
            [musnake.shared.model :as m]
            [devcards.core :refer [defcard defcard-rg]]))

(enable-console-print!)

(defcard
  "# Board")

(defn pos->board-pos [p]
  {:x (* (:x p) m/board-cell-size)
   :y (* (:y p) m/board-cell-size)})

(defn object [pos color]
  [:rect (into (pos->board-pos pos)
               {:width m/board-cell-size :height m/board-cell-size
                :fill color})])

(defn food [food]
  [object food "green"])

(defn snake-body [snake color]
  (into
   [:svg]
   (for [o (:body snake)]
     [object o color])))

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
        svg-ref-with-listener (atom nil)]
    (fn [{client-id :client-id
          snakes    :snakes
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
               [food food-pos]]
              (for [[id snake] snakes]
                [snake-body snake (if (= id client-id)
                                    "blue" "red")]))]])))

(defcard empty-board
  "An empty board with just one food rendered on it.  Note that a valid board
  always has at least one food placed on it."
  (reagent/as-element
   [board m/client-initial-state #()]))

(defcard-rg user-can-move-around
  (fn [app-state _]
    [board (-> @app-state
               (get-in [:rooms :lobby])
               (assoc :client-id :test))
     (fn [d]
       (swap! app-state
              #(-> %
                   (m/change-direction :test d)
                   (m/process-frame))))])
  (atom (-> m/server-initial-state
            (m/connect :test :lobby {:x 10 :y 10})))
  {:inspect-data true})

(defcard "# Game Selection")

(defn menu [& children]
  (into
   [:div {:style {:display         "flex"
                  :flex-direction  "column"
                  :gap             "1em"
                  :align-items     "strech"
                  :text-align      "center"
                  :justify-content "center"
                  :height          "100%"}}]
   children))

(defn menu-item [& children]
  (into [:div] children))

(defn menu-button [value on-click]
  [menu-item
   [:button {:style {:width            "80%"
                     :padding          "1em"
                     :font-size        "1em"
                     :background-color "lightgreen"}
             :on-click on-click}
    value]])

(defn game-screen [& children]
  (into
   [:div {:style {:width            "500px"
                  :height           "500px"
                  :background-color "lightyellow"}}]
   children))

(defn start-page [{:keys [on-play-now on-new-game on-join]}]
  [game-screen
   [menu
    [menu-button [:strong "Play Now"] on-play-now]
    [menu-button "New Game" on-new-game]
    [menu-button "Join Game" on-join]]])

(defcard-rg start-page-example
  [start-page {:on-play-now #(js/alert "Play Now")
               :on-new-game #(js/alert "New Game")
               :on-join #(js/alert "Join Game")}])

(defn menu-copypasta-item [{:keys [value copied? on-copy]}]
  [menu-item
   [:center
    [:div {:style {:box-sizing       "border-box"
                   :width            "80%"
                   :padding          "1em"
                   :font-size        "1em"
                   :background-color (if copied? "lightblue" "lightgreen")
                   :border "1px solid black"}
           :on-click (fn [] (.then (.writeText (.. js/navigator -clipboard) value)
                                   (fn [] (on-copy value))))}
     value]]])

(defn new-game-page [{:keys [code copied? on-play on-copy]}]
  [game-screen
   [menu
    [menu-copypasta-item {:value code :copied? copied? :on-copy on-copy}]
    [menu-button [:strong "Play Now"] on-play]]])

(defcard-rg new-game-page-example
  (fn [copied?]
    [new-game-page {:code "3E07CF78-83F2-4BC4-976B-CD6D9838112F"
                    :copied? @copied?
                    :on-play #(js/alert "Play Now")
                    :on-copy #(reset! copied? (not @copied?))}])
  (atom false))

(defn menu-input [{:keys [value placeholder on-change]}]
  [menu-item
   [:input {:type "input"
            :value value
            :placeholder placeholder
            :style {:box-sizing       "border-box"
                    :width            "80%"
                    :padding          "1em"
                    :font-size        "1em"
                    :background-color "lightgreen"}
            :on-change #(on-change (-> % .-target .-value))}]])

(defn join-page [{:keys [code on-change-code on-play]}]
  [game-screen
   [menu
    [menu-input {:value code
                 :placeholder "Enter the game code!"
                 :on-change #(on-change-code %)}]
    [menu-button [:strong "Play Now"] on-play]]])

(defcard-rg join-page-example
  (fn [app-state _]
    [join-page {:code @app-state
                :on-change-code #(reset! app-state %)
                :on-play #(js/alert (str "Entered code: " @app-state))}])
  (atom ""))


(defn waiting-page []
  [game-screen
   [menu
    [:div "Waiting for the snakver ..."]]])

(defcard-rg waiting-page-card
  [waiting-page])

(defn game-view [app-state dispatch]
  (case (:view app-state)
    start-page [start-page {:on-play-now #(dispatch :change-view 'game)
                            :on-new-game #(dispatch :new-game)
                            :on-join #(dispatch :change-view 'join-page)}]
    new-game-page [new-game-page {:code (:room-id app-state)
                                  :copied? (:room-id-copied? app-state)
                                  :on-play #(dispatch :change-view 'game)
                                  :on-copy #(dispatch :copy-room-id)}]
    join-page [join-page {:code (:room-code app-state)
                          :on-change-code #(dispatch :change-room-code %)
                          :on-play #(dispatch :join-room)}]
    game [board app-state #(dispatch :change-direction %)]
    waiting [waiting-page]))
