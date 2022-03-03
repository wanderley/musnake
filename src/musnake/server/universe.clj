(ns musnake.server.universe
  (:require [chord.http-kit :refer [with-channel]]
            [clojure.core.async :as async]
            [medley.core :refer [random-uuid]]))

(defn make-universe [app-state {:keys [tick-rate on-event]}]
  "Creates an universe where

  - `tick-rate` is an interval in seconds which the clock will tick in the
    universe.  Default is 1/28 seconds.

  - `on-event` is a function called for each event that happens on the universe.

  These are the possible events in the universe:

  - `tick` occurs for each clock tick.  The creator is responsible for computing
    the next state and describing the side-effects of the event like sending an
    event to a specific world.

  - `connect` occurs for every world creation.  This event always has one
    parameter `client-id` that identify the world.  A webpage can have zero, one
    or more worlds running simultaneously.

  - `disconnect` occurs when a world is destroyed.  After this event, the
    universe can't communicate with the world or vice e versa.  This event
    always has one parameter `client-id` which represents the world that
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
  (let [main-chan (async/chan (async/sliding-buffer 10))
        main-mult (async/mult main-chan)
        connections (atom {})
        send-message (fn [client-id params]
                       (async/put! (get-in @connections [client-id :tap])
                                   params))
        dispatch (fn [& params]
                   (swap! app-state
                          #(let [next
                                 (apply on-event (into [(first params) %]
                                                       (rest params)))]
                             (when-let [[client-id & params] (:client-message next)]
                               (send-message client-id params))
                             (when-let [messages (:client-messages next)]
                               (doseq [[client-id & params] messages]
                                 (send-message client-id params)))
                             (if (:state next) (:state next) %))))]
    {:big-bang!
     (fn big-bang! []
       (future (while true
                 (do (Thread/sleep (* 1000 (or tick-rate 1/28)))
                     (try
                       (dispatch 'tick)
                       (catch Exception e
                         (println (.getMessage e))))))))

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
                    (dispatch 'disconnect client-id)))))))))}))
