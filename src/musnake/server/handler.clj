(ns musnake.server.handler
  (:require [chord.http-kit :refer [with-channel]]
            [clojure.core.async :as async]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [medley.core :refer [random-uuid]]
            [org.httpkit.server :as hk]
            [ring.util.response :as resp]))

;;; State

(def initial-state {})
(defonce app-state (atom initial-state))

;;; Communication

(defonce main-chan (async/chan))

(defonce main-mult (async/mult main-chan))

(defn ws-handler
  [req]
  (with-channel req client-channel
    (let [client-tap (async/chan)
          client-id (.toString (random-uuid))]
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
               ;; process message
               (recur))
             (do
               ;; tell clients about disconnection?
               (async/untap main-mult client-tap)))))))))

(defroutes app
  (GET "/ws" [] ws-handler)
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/")
  (route/not-found "<h1>Page not found!  You were supposed to be playing with snakes!</h1>"))
