(ns musnake.client.core
  (:require [cljs.core.async :as async :include-macros true]
            [musnake.client.server :refer [connect!]]
            [musnake.shared.model :as m]
            [musnake.client.views :refer [board]]
            [reagent.core :as reagent :refer [atom]]
            [reagent.dom :as rd]))

(enable-console-print!)

(defonce app-state (atom m/server-initial-state))

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

(defn app []
  [:div {:style {:position "absolute"
                 :background "lightgreen"
                 :width "90%"
                 :max-width "500px"}}
   [:center
    [:h1 "μSnake"]
    [board @app-state #(server-emit! 'change-direction %)]]])

(rd/render [app] (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
