(ns goatverse.universe
  (:require [chord.http-kit :refer [with-channel]]
            [clojure.core.async :as async]
            [medley.core :refer [random-uuid]]))

(defn make-universe
  "Creates an universe where

  - `tick-rate` is an interval in seconds which the clock will tick in the
    universe.  Default is 1/28 seconds.

  - `on-event` is a function called for each event that happens on the universe.

  These are the possible events in the universe:

  - `tick` occurs for each clock tick.  The creator is responsible for computing
    the next state and describing the side-effects of the event like sending an
    event to a specific world.

  - `connect` occurs for every world creation.  This event always has one
    parameter `world-id` that identify the world.  A webpage can have zero, one
    or more worlds running simultaneously.

  - `disconnect` occurs when a world is destroyed.  After this event, the
    universe can't communicate with the world or vice e versa.  This event
    always has one parameter `world-id` which represents the world that
    disconnected.

  - `big-chrunch` occurs when the universe expansion reverses causing it to
    collapse.  This will happen when the callback `big-chrunch!` is called.  At
    this moment, all worlds will be destroyed and the state of the universe will
    be restored to its initial state.  The universe will take care of destroying
    the worlds, while the creator will provide the initial state.

  - Besides these events, a world can cause an event on the universe.  The event
    will always have the type as first parameter followed by the event's
    parameters.  The creator is responsible to handle these events on `on-event`
    callback.

  This function returns a map of functions where

  - `big-bang!` is a function that causes an explosion that creates the universe.

  - `big-chrunch!` is a function that causes the collapse of the universe.

  - `ws-handler` is a function that handles all connection/message logic (using
    sockets)."
  [app-state {:keys [tick-rate on-event]}]
  (let [main-chan (async/chan (async/sliding-buffer 10))
        main-mult (async/mult main-chan)
        connections (atom {})
        send-message (fn [world-id params]
                       (async/put! (get-in @connections [world-id :tap])
                                   params))
        dispatch (fn [& params]
                   (swap! app-state
                          #(let [next
                                 (apply on-event (into [(first params) %]
                                                       (rest params)))]
                             (when-let [[world-id & params] (:world-message next)]
                               (send-message world-id params))
                             (when-let [messages (:world-messages next)]
                               (doseq [[world-id & params] messages]
                                 (send-message world-id params)))
                             (if (:state next) (:state next) %))))]
    {:big-bang!
     (fn big-bang! []
       (when tick-rate
         (future (while true
                   (do (Thread/sleep (* 1000 tick-rate))
                       (try
                         (dispatch 'tick)
                         (catch Exception e
                           (println (.getMessage e)))))))))

     :big-chrunch!
     (fn big-crunch! []
       (dispatch 'big-chrunch)
       (doseq [[_ {:keys [channel tap]}] @connections]
         (async/untap main-mult tap)
         (async/close! channel)
         (async/close! tap))
       (reset! connections {}))

     :ws-handler
     (fn ws-handler
       [req]
       (with-channel req world-channel
         (let [world-tap (async/chan (async/sliding-buffer 10))
               world-id (.toString (random-uuid))]
           (swap! connections assoc world-id {:channel world-channel
                                               :tap     world-tap})
           (dispatch 'connect world-id)
           (async/put! world-channel ['world-id world-id])
           (async/tap main-mult world-tap)
           (async/go-loop []
             (async/alt!
               world-tap
               ([message]
                (if message
                  (do
                    (async/>! world-channel message)
                    (recur))
                  (async/close! world-channel)))

               world-channel
               ([{:keys [message]}]
                (if message
                  (do
                    (apply dispatch (concat (list (first message) world-id)
                                            (rest message)))
                    (recur))
                  (do
                    (async/untap main-mult world-tap)
                    (async/close! world-tap)
                    (swap! connections dissoc world-id)
                    (dispatch 'disconnect world-id)))))))))}))
