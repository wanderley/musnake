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
  (m/change-direction app-state client-id direction))

(defevent! 'new-game [app-state client-id]
  (let [state (m/new-game! app-state client-id)
        tap (get-in @connections [client-id :tap])
        room-id (get-in state [:client-rooms client-id])
        room (extract-client-state state client-id room-id)]
    (async/put! tap ['join-room room])
    state))

(defevent! 'join [app-state client-id room-id]
  (if (get-in app-state [:rooms room-id])
    (let [state (m/connect! app-state room-id client-id)
          tap (get-in @connections [client-id :tap])
          room-id (get-in state [:client-rooms client-id])
          room (extract-client-state state client-id room-id)]
      (async/put! tap ['play room])
      state)
    (do
      (async/put! (get-in @connections [client-id :tap]) ['join-failed])
      app-state)))

(defevent! 'connect [app-state client-id]
  (m/connect! app-state :lobby client-id))

(defevent! 'disconnect [app-state client-id]
  (m/disconnect app-state client-id))

(defn toc! []
  (let [state (swap! app-state m/process-frame)]
    (doseq [[client-id {:keys [tap]}] @connections]
      (let [room-id (get-in state [:client-rooms client-id])
            room (extract-client-state state client-id room-id)]
        (async/put! tap ['state room])))
    state))

(comment
  ;; Start game loop on server
  (defonce tic! (future (while true
                          (do (Thread/sleep 100)
                              (try
                                (toc!)
                                (catch Exception e
                                  (println (.getMessage e)))))))))

;;; Handler

(defn ws-handler
  [req]
  (with-channel req client-channel
    (let [client-tap (async/chan (async/sliding-buffer 10))
          client-id (.toString (random-uuid))]
      (swap! connections assoc client-id {:channel client-channel
                                          :tap     client-tap})
      (swap! app-state #(event! 'connect % client-id))
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
               (swap! app-state
                      #(apply event! (concat (list (first message) %)
                                             (list client-id)
                                             (rest message))))
               (recur))
             (do
               (async/untap main-mult client-tap)
               (async/close! client-tap)
               (swap! connections dissoc client-id)
               (swap! app-state #(event! 'disconnect % client-id))))))))))

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
  (GET "/ws" [] ws-handler)
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/")
  (route/not-found "<h1>Page not found!  You were supposed to be playing with snakes!</h1>"))
