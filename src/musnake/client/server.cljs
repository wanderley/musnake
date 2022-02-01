(ns musnake.client.server
  (:require [chord.client :refer [ws-ch]]
            [cljs.core.async :as async :include-macros true]))

(defn receive-messages! [ws-channel incoming-messages]
  (async/go-loop []
    (let [{:keys [message]} (async/<! ws-channel)]
      (async/>! incoming-messages message)
      (recur))))

(defn send-messages! [ws-channel outgoing-messages]
  (async/go-loop []
    (when-let [message (async/<! outgoing-messages)]
      (async/>! ws-channel message)
      (recur))))

(defn connect!
  "Connects with the server and starts the input and output channels."
  [ws-url outgoing-messages incoming-messages]
  (async/go
    (let [{:keys [ws-channel error]} (async/<! (ws-ch ws-url))]
      (if error
        (println "Connection failed with" (str error))
        (do
          (println "Connected!")
          (send-messages! ws-channel outgoing-messages)
          (receive-messages! ws-channel incoming-messages))))))
