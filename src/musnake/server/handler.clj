(ns musnake.server.handler
  (:require [chord.http-kit :refer [with-channel]]
            [clojure.core.async :as async]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [medley.core :refer [random-uuid]]
            [musnake.shared.model :as m]
            [ring.util.response :as resp]))

;;; Connections

(defonce main-chan (async/chan (async/sliding-buffer 10)))
(defonce main-mult (async/mult main-chan))
(defonce connections (atom {}))

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

(defn toc! []
  (let [state (swap! app-state m/process-frame)]
    (doseq [[client-id {:keys [tap]}] @connections]
      (let [room-id (get-in state [:client-rooms client-id])
            room (extract-client-state state client-id room-id)]
        (async/put! tap ['state room])))
    {:state state}))

(comment
  ;; Start game loop on server
  (defonce tic! (future (while true
                          (do (Thread/sleep 100)
                              (try
                                (toc!)
                                (catch Exception e
                                  (println (.getMessage e)))))))))

;;; Handler

(defn make-world [app-state]
  (let [dispatch (fn [& params]
                   (swap! app-state
                          #(let [next
                                 (apply event! (into [(first params) %]
                                                     (rest params)))]
                             (when-let [[client-id & params] (:client-message next)]
                               (async/put! (get-in @connections [client-id :tap])
                                           params))
                             (if (:state next)
                               (:state next)
                               %))))]
    (fn ws-handler
      [req]
      (with-channel req client-channel
        (let [client-tap (async/chan (async/sliding-buffer 10))
              client-id (.toString (random-uuid))]
          (swap! connections assoc client-id {:channel client-channel
                                              :tap     client-tap})
          (dispatch 'connect client-id)
          (async/put! client-channel ['client-id client-id])
          (async/tap main-mult client-tap)
          (async/go-loop []
            (async/alt!
              client-tap
              ([message]
               (if message
                 (do
                   (async/>! client-channel message)
                   (recur))
                 (async/close! client-channel)))

              client-channel
              ([{:keys [message]}]
               (if message
                 (do
                   (apply dispatch (concat (list (first message) client-id)
                                           (rest message)))
                   (recur))
                 (do
                   (async/untap main-mult client-tap)
                   (async/close! client-tap)
                   (swap! connections dissoc client-id)
                   (dispatch 'disconnect client-id)))))))))))

(defn restart-server-handler! [_]
  (reset! app-state m/server-initial-state)
  (doseq [[_ {:keys [channel tap]}] @connections]
    (async/untap main-mult tap)
    (async/close! channel)
    (async/close! tap))
  (reset! connections {})
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body    "Server restarted!"})

(defroutes app
  (GET "/restart" [] restart-server-handler!)
  (GET "/ws" [] (make-world app-state))
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/")
  (route/not-found "<h1>Page not found!  You were supposed to be playing with snakes!</h1>"))
