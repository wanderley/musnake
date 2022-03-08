(ns goatverse.world
  (:require [cljs.core.async :as async :include-macros true]
            [chord.client :refer [ws-ch]]))

(defn- receive-messages! [ws-channel incoming-messages]
  (async/go-loop []
    (let [{:keys [message]} (async/<! ws-channel)]
      (async/>! incoming-messages message)
      (recur))))

(defn- send-messages! [ws-channel outgoing-messages]
  (async/go-loop []
    (when-let [message (async/<! outgoing-messages)]
      (async/>! ws-channel message)
      (recur))))

(defn- connect!
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


(defn make-world [state {:keys [on-render on-message on-server-message]}]
  (let [incoming-messages (async/chan (async/sliding-buffer 10))
        outgoing-messages (async/chan (async/sliding-buffer 10))
        server-dispatch! (fn [& message]
                           (async/put! outgoing-messages message))
        consume-server-message (async/go-loop []
                                 (let [message (async/<! incoming-messages)]
                                   (swap! state
                                          #(apply on-server-message
                                                  (into [(first message) %]
                                                        (rest message)))))
                                 (recur))
        connection (connect! (str
                              (case (.. js/document -location -protocol)
                                "https:" "wss:"
                                "ws:")
                              "//" (.. js/document -location -host) "/ws")
                             outgoing-messages
                             incoming-messages)
        dispatch (fn [type & params]
                   (let [next (apply on-message (into [type @state] params))]
                     (when (:state next)
                       (swap! state #(identity (:state next))))
                     (when (:server-dispatch next)
                       (apply server-dispatch! (:server-dispatch next)))))]
    (fn []
      (on-render state dispatch))))
