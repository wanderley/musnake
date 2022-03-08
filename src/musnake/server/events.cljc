(ns musnake.server.events
  (:require [musnake.shared.model :as m]))

;;; Events

(defn- extract-client-state [app-state client-id room-id]
  (-> app-state
      (get-in [:rooms room-id])
      (assoc :client-id client-id)
      (assoc :room-id room-id)))

(defmulti event! (fn [type & _] type))

(defmethod event! :default [_ app-state & _]
  {:state app-state})

(defmethod event! 'change-direction [_ app-state client-id direction]
  {:state (m/change-direction app-state client-id direction)})

(defmethod event! 'new-game [_ app-state client-id]
  (let [state (m/new-game! app-state client-id)
        room-id (get-in state [:client-rooms client-id])
        room (extract-client-state state client-id room-id)]
    {:state state
     :client-messages [[client-id 'join-room room]]}))

(defmethod event! 'join [_ app-state client-id room-id]
  (if (get-in app-state [:rooms room-id])
    (let [state (m/connect! app-state room-id client-id)
          room-id (get-in state [:client-rooms client-id])
          room (extract-client-state state client-id room-id)]
      {:state state
       :client-messages [[client-id 'play room]]})
    {:state app-state
     :client-messages [[client-id 'join-failed]]}))

(defmethod event! 'connect [_ app-state client-id]
  (let [next (m/connect! app-state :lobby client-id)]
    {:state next
     :client-messages
     [[client-id 'state
       (extract-client-state next client-id :lobby)]]}))

(defmethod event! 'disconnect [_ app-state client-id]
  {:state (m/disconnect app-state client-id)})

(defmethod event! 'tick [_ app-state]
  (let [state (m/process-frame app-state)
        client-messages (map
                         (fn [[client-id room-id]]
                           [client-id
                            'state
                            (extract-client-state state client-id room-id)])
                         (:client-rooms state))]
    {:state state
     :client-messages client-messages}))

(defmethod event! 'big-chrunch [_ _]
  {:state m/server-initial-state})
