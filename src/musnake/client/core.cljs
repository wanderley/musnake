(ns musnake.client.core
  (:require [cljs.core.async :as async :include-macros true]
            [musnake.client.server :refer [connect!]]
            [musnake.shared.model :as m]
            [musnake.client.views :refer [start-page
                                          new-game-page
                                          join-page
                                          board
                                          waiting-page]]
            [reagent.core :as reagent :refer [atom]]
            [reagent.dom :as rd]))

(enable-console-print!)

(defonce app-state (atom m/client-initial-state))

;;; Server

(defonce incoming-messages (async/chan (async/sliding-buffer 10)))
(defonce outgoing-messages (async/chan (async/sliding-buffer 10)))
(defn server-emit! [& message]
  (async/put! outgoing-messages message))
(defonce consume-server-message
  (async/go-loop []
    (let [message (async/<! incoming-messages)]
      (case (first message)
        state (swap! app-state merge (second message))
        client-id (swap! app-state assoc :client-id (second message))
        join-room (swap! app-state #(-> (second message)
                                        (merge %)
                                        (assoc :view 'new-game-page)))
        join-failed (swap! app-state #(-> %
                                          (assoc :room-code "")
                                          (assoc :view 'join-page)))
        play (swap! app-state #(-> (second message)
                                   (merge %)
                                   (assoc :view 'game)))
        nil))
    (recur)))
(defonce connection
  (connect! (str
             (case (.. js/document -location -protocol)
               "https:" "wss:"
               "ws:")
             "//" (.. js/document -location -host) "/ws")
            outgoing-messages
            incoming-messages))

;;; Views

(defmulti message (fn [type & _] type))

(defmethod message :change-view [_ state view]
  {:state (assoc state :view view)})

(defmethod message :new-game [_ state]
  {:state (assoc state :view 'waiting)
   :server-emit ['new-game]})

(defmethod message :copy-room-id [_ state]
  {:state (assoc state :room-id-copied? true)})

(defmethod message :change-room-code [_ state code]
  {:state (assoc state :room-code code)})

(defmethod message :join-room [_ state]
  {:state (assoc state :view 'waiting)
   :server-emit ['join (:room-code state)]})

(defmethod message :change-direction [_ state direction]
  {:server-emit ['change-direction direction]})

(defn app [app-state dispatch]
  [:div {:style {:position "absolute"
                 :background "lightgreen"
                 :width "90%"
                 :max-width "500px"}}
   [:center
    [:h1 "μSnake"]
    (case (:view @app-state)
      start-page [start-page {:on-play-now #(dispatch :change-view 'game)
                              :on-new-game #(dispatch :new-game)
                              :on-join #(dispatch :change-view 'join-page)}]
      new-game-page [new-game-page {:code (:room-id @app-state)
                                    :copied? (:room-id-copied? @app-state)
                                    :on-play #(dispatch :change-view 'game)
                                    :on-copy #(dispatch :copy-room-id)}]
      join-page [join-page {:code (:room-code @app-state)
                            :on-change-code #(dispatch :change-room-code %)
                            :on-play #(dispatch :join-room)}]
      game [board @app-state #(dispatch :change-direction %)]
      waiting [waiting-page])]])

(defn world [state {:keys [on-render on-message]}]
  (let [dispatch (fn [type & params]
                   (let [next (apply on-message (into [type @state] params))]
                     (when (:state next)
                       (swap! state #(identity (:state next))))
                     (when (:server-emit next)
                       (apply server-emit! (:server-emit next)))))]
    (on-render state dispatch)))

(rd/render [world app-state {:on-render app :on-message message}]
           (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
