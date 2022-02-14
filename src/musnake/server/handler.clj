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

(defmulti event! (fn [type app-state & params] type))
(defmacro defevent! [type args & body]
 `(defmethod ~'event! ~type [~'_ ~@args] (do ~@body)))

(defevent! :default [client-id app-state & params] app-state)

(defevent! 'change-direction [client-id direction]
  (swap! app-state m/change-direction client-id direction))

(defevent! 'connect [client-id]
  (swap! app-state m/connect! client-id))

(defevent! 'disconnect [client-id]
  (swap! app-state m/disconnect client-id))

(defn toc! []
  (let [state (swap! app-state m/process-frame)]
    (doseq [[client-id {:keys [tap]}] @connections]
      (let [room-id (get-in state [:client-rooms client-id])
            room (get-in state [:rooms room-id])]
        (async/put! tap ['state room])))))

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
      (event! 'connect client-id)
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
               (apply event! (concat (list (first message))
                                     (list client-id)
                                     (rest message)))
               (recur))
             (do
               (async/untap main-mult client-tap)
               (async/close! client-tap)
               (swap! connections dissoc client-id)
               (event! 'disconnect client-id)))))))))

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
