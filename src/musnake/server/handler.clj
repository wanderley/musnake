(ns musnake.server.handler
  (:require [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [goatverse.universe :refer [make-universe]]
            [musnake.server.messages :refer [message]]
            [musnake.shared.model :as m]
            [ring.util.response :as resp]))

;;; State

(defonce app-state (atom m/universe-initial-state))

;;; Handler

(defonce universe
  (make-universe app-state
                 {:tick-rate 1/10
                  :on-event message}))
(defonce big-bang! (:big-bang! universe))

(comment
  ;; Start game loop on universe
  (big-bang!))

(defroutes app
  (GET "/restart" [] (fn [_]
                       ((:big-chrunch! universe))
                       {:status 200
                        :headers {"Content-Type" "text/plain"}
                        :body    "Big Chrunch just happened!"}))
  (GET "/ws" [] (:ws-handler universe))
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/")
  (route/not-found "<h1>Page not found!  You were supposed to be playing with snakes!</h1>"))
