(ns musnake.server.messages
  (:require [musnake.shared.model :as m]))

;;; Events

(defn- extract-world-state [app-state world-id room-id]
  (-> app-state
      (get-in [:rooms room-id])
      (assoc :world-id world-id)
      (assoc :room-id room-id)))

(defmulti message (fn [type & _] type))

(defmethod message :default [_ app-state & _]
  {:state app-state})

(defmethod message 'change-direction [_ app-state world-id direction]
  {:state (m/change-direction app-state world-id direction)})

(defmethod message 'new-game [_ app-state world-id]
  (let [state (m/new-game! app-state world-id)
        room-id (get-in state [:world-rooms world-id])
        room (extract-world-state state world-id room-id)]
    {:state state
     :world-messages [[world-id 'join-room room]]}))

(defmethod message 'join [_ app-state world-id room-id]
  (if (get-in app-state [:rooms room-id])
    (let [state (m/connect! app-state room-id world-id)
          room-id (get-in state [:world-rooms world-id])
          room (extract-world-state state world-id room-id)]
      {:state state
       :world-messages [[world-id 'play room]]})
    {:state app-state
     :world-messages [[world-id 'join-failed]]}))

(defmethod message 'connect [_ app-state world-id]
  (let [next (m/connect! app-state :lobby world-id)]
    {:state next
     :world-messages
     [[world-id 'state
       (extract-world-state next world-id :lobby)]]}))

(defmethod message 'disconnect [_ app-state world-id]
  {:state (m/disconnect app-state world-id)})

(defmethod message 'tick [_ app-state]
  (let [state (m/process-frame app-state)
        world-messages (map
                         (fn [[world-id room-id]]
                           [world-id
                            'state
                            (extract-world-state state world-id room-id)])
                         (:world-rooms state))]
    {:state state
     :world-messages world-messages}))

(defmethod message 'big-chrunch [_ _]
  {:state m/universe-initial-state})
