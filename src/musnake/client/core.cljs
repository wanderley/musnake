(ns musnake.client.core
  (:require [goatverse.world :refer [make-world]]
            [musnake.client.messages :refer [message]]
            [musnake.client.views :refer [game-view]]
            [musnake.shared.model :as m]
            [reagent.core :as reagent :refer [atom]]
            [reagent.dom :as rd]))

(enable-console-print!)

(defonce app-state (atom m/world-initial-state))

(defn app [app-state dispatch]
  [:div {:style {:position "absolute"
                 :background "lightgreen"
                 :width "90%"
                 :max-width "500px"}}
   [:center
    [:h1 "μSnake"]
    [game-view @app-state dispatch]]])

(defonce world (make-world app-state {:on-render app
                                      :on-message message}))

(rd/render [world]
           (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
