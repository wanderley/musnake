(ns musnake.server.handler
  (:require [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [musnake.shared.model :as m]
            [musnake.server.universe :refer [make-universe]]
            [ring.util.response :as resp]))

;;; State

(defonce app-state (atom m/server-initial-state))

;;; Events

(defmulti event! (fn [type & params] type))
(defmacro defevent! [type args & body]
  `(defmethod ~'event! ~type [~'_ ~@args] (do ~@body)))

(defn extract-client-state [app-state client-id room-id]
  (-> app-state
      (get-in [:rooms room-id])
      (assoc :client-id client-id)
      (assoc :room-id room-id)))

(defevent! :default [_ app-state & params] app-state)

(defevent! 'change-direction [app-state client-id direction]
  {:state (m/change-direction app-state client-id direction)})

(defevent! 'new-game [app-state client-id]
  (let [state (m/new-game! app-state client-id)
        room-id (get-in state [:client-rooms client-id])
        room (extract-client-state state client-id room-id)]
    {:state state
     :client-message [client-id 'join-room room]}))

(defevent! 'join [app-state client-id room-id]
  (if (get-in app-state [:rooms room-id])
    (let [state (m/connect! app-state room-id client-id)
          room-id (get-in state [:client-rooms client-id])
          room (extract-client-state state client-id room-id)]
      {:state state
       :client-message [client-id 'play room]})
    {:state app-state
     :client-message [client-id 'join-failed]}))

(defevent! 'connect [app-state client-id]
  {:state (m/connect! app-state :lobby client-id)})

(defevent! 'disconnect [app-state client-id]
  {:state (m/disconnect app-state client-id)})

(defevent! 'tick [app-state]
  (let [state (m/process-frame app-state)
        client-messages (map
                         (fn [[client-id room-id]]
                           [client-id
                            'state
                            (extract-client-state state client-id room-id)])
                         (:client-rooms state))]
    {:state state
     :client-messages client-messages}))

(defevent! 'big-chrunch [app-state]
  {:state m/server-initial-state})

(comment
  ;; Start game loop on server
  (big-bang!))

;;; Handler

(defonce universe
  (make-universe app-state
                 {:tick-rate 1/10
                  :on-event event!}))
(defonce big-bang! (:big-bang! universe))

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
