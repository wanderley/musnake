(ns musnake.client.core
  (:require [musnake.shared.model :as m]
            [musnake.client.world :refer [make-world]]
            [musnake.client.events :refer [server-message message]]
            [musnake.client.views :refer [start-page
                                          new-game-page
                                          join-page
                                          board
                                          waiting-page]]
            [reagent.core :as reagent :refer [atom]]
            [reagent.dom :as rd]))

(enable-console-print!)

(defonce app-state (atom m/client-initial-state))

(defn game-view [app-state dispatch]
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
    waiting [waiting-page]))

(defn app [app-state dispatch]
  [:div {:style {:position "absolute"
                 :background "lightgreen"
                 :width "90%"
                 :max-width "500px"}}
   [:center
    [:h1 "μSnake"]
    [game-view app-state dispatch]]])

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
