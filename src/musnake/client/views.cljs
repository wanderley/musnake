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

(defn start-page []
  [game-screen
   [menu
    [menu-button [:strong "Play Now"] #(js/alert "Play Now")]
    [menu-button "New Game" #(js/alert "New Game")]
    [menu-button "Join Game" #(js/alert "Join Game")]]])

(defcard-rg start-page-example
  [start-page])

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
                 :on-change #(on-change-code (clojure.string/upper-case %))}]
    [menu-button [:strong "Play Now"] on-play]]])

(defcard-rg join-page-example
  (fn [app-state _]
    [join-page {:code @app-state
                :on-change-code #(reset! app-state %)
                :on-play #(js/alert (str "Entered code: " @app-state))}])
  (atom ""))
