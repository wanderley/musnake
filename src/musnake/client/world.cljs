(ns musnake.client.world
  (:require [cljs.core.async :as async :include-macros true]
            [musnake.client.server :refer [connect!]]))

(defn make-world [state {:keys [on-render on-message on-server-message]}]
  (let [incoming-messages (async/chan (async/sliding-buffer 10))
        outgoing-messages (async/chan (async/sliding-buffer 10))
        server-emit! (fn [& message]
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
                     (when (:server-emit next)
                       (apply server-emit! (:server-emit next)))))]
    (fn []
      (on-render state dispatch))))
