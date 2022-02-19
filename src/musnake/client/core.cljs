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

(defn app []
  [:div {:style {:position "absolute"
                 :background "lightgreen"
                 :width "90%"
                 :max-width "500px"}}
   [:center
    [:h1 "μSnake"]
    (case (:view @app-state)
      start-page [start-page {:on-play-now #(swap! app-state assoc :view 'game)
                              :on-new-game #(do (swap! app-state assoc :view 'waiting)
                                                (server-emit! 'new-game))
                              :on-join #(swap! app-state assoc :view 'join-page)}]
      new-game-page [new-game-page {:code (:room-id @app-state)
                                    :copied? (:room-id-copied? @app-state)
                                    :on-play #(swap! app-state assoc :view 'game)
                                    :on-copy #(swap! app-state assoc :room-id-copied? true)}]
      join-page [join-page {:code (:room-code @app-state)
                            :on-change-code #(swap! app-state assoc :room-code %)
                            :on-play #(do
                                        (swap! app-state assoc 'waiting)
                                        (server-emit! 'join (:room-code @app-state)))}]
      game [board @app-state #(server-emit! 'change-direction %)]
      waiting [waiting-page])]])

(rd/render [app] (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
