(ns musnake.client.core
  (:require [musnake.shared.model :as m]
            [musnake.client.world :refer [make-world]]
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

(defmulti server-message (fn [type & _] type))

(defmethod server-message 'state [_ state new-state]
  (merge state new-state))

(defmethod server-message 'client-id [_ state client-id]
  (assoc state :client-id client-id))

(defmethod server-message 'join-room [_ state new-state]
  (-> state
      (merge new-state)
      (assoc :view 'new-game-page)))

(defmethod server-message 'join-failed [_ state]
  (-> state
      (assoc :room-code "")
      (assoc :view 'join-page)))

(defmethod server-message 'play [_ state new-state]
  (-> state
      (merge new-state)
      (assoc :view 'game)))

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

(defonce world (make-world app-state {:on-render app
                                      :on-message message
                                      :on-server-message server-message}))

(rd/render [world]
           (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
