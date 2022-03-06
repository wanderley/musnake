(ns musnake.client.core
  (:require [musnake.shared.model :as m]
            [musnake.client.world :refer [make-world]]
            [musnake.client.events :refer [server-message message]]
            [musnake.client.views :refer [game-view]]
            [reagent.core :as reagent :refer [atom]]
            [reagent.dom :as rd]))

(enable-console-print!)

(defonce app-state (atom m/client-initial-state))

(defn app [app-state dispatch]
  [:div {:style {:position "absolute"
                 :background "lightgreen"
                 :width "90%"
                 :max-width "500px"}}
   [:center
    [:h1 "μSnake"]
    [game-view @app-state dispatch]]])

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
