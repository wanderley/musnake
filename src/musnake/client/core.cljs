(ns musnake.client.core
  (:require [chord.client :refer [ws-ch]]
            [cljs.core.async :as async :include-macros true]
            [medley.core :refer [abs]]
            [musnake.client.server :refer [connect!]]
            [musnake.shared.model :as m]
            [reagent.core :as reagent :refer [atom]]
            [reagent.dom :as rd]))

(enable-console-print!)

(def app-state (atom m/client-initial-state))

;;; Server

(defonce incoming-messages (async/chan (async/sliding-buffer 10)))
(defonce outgoing-messages (async/chan (async/sliding-buffer 10)))
(defn server-emit! [& message]
  (async/put! outgoing-messages message))
(async/go-loop []
  (let [message (async/<! incoming-messages)]
    (case (first message)
      state (swap! app-state merge (second message))
      client-id (swap! app-state assoc :client-id (second message))
      nil))
  (recur))
(connect! (str
           (case (.. js/document -location -protocol)
             "https:" "wss:"
             "ws:")
           "//" (.. js/document -location -host) "/ws")
          outgoing-messages
          incoming-messages)

;;; Views

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

(defn board [{client-id :client-id
              snakes    :snakes
              board     :board
              food-pos  :food}]
  (let [last-touch (atom nil)]
    [:div {:style {:padding "1em"}}
     (into [:svg {:width  "100%"
                  :height "100%"
                  :viewBox "0 0 500 500"
                  :preserveAspectRatio "xMidYMid meet"
                  :focusable true
                  :tabIndex 0
                  :background "lightyellow"
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
                                (server-emit! 'change-direction d))))
                           (.addEventListener
                            el "touchstart"
                            (fn [to]
                              (.preventDefault to)
                              (let [curr-touch {:x (-> to .-targetTouches last .-pageX)
                                                :y (-> to .-targetTouches last .-pageY)}]
                                (reset! last-touch curr-touch))))
                           (.addEventListener
                            el "touchmove"
                            (fn [to]
                              (.preventDefault to)
                              (let [curr-touch {:x (-> to .-targetTouches last .-pageX)
                                                :y (-> to .-targetTouches last .-pageY)}]
                                (when-let [d (touch-move->direction
                                              (or @last-touch curr-touch) curr-touch)]
                                  (server-emit! 'change-direction d)))))))}

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
              board]))]))

(defn app []
  [:div {:style {:margin "0"
                 :position "absolute"
                 :background "lightgreen"
                 :top "50%"
                 :left "50%"
                 :width "90%"
                 :max-width "500px"
                 :-ms-transform "translate(-50%, -50%)"
                 :transform "translate(-50%, -50%)"
                 :border "1px solid black"}}
   [:center
    [:h1 "μSnake"]
    [board @app-state]]])

(rd/render [app] (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
