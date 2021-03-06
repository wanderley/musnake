(ns musnake.client.messages)

(defmulti message (fn [type & _] type))

(defmethod message 'state [_ state new-state]
  {:state (merge state new-state)})

(defmethod message 'world-id [_ state world-id]
  {:state (assoc state :world-id world-id)})

(defmethod message 'join-room [_ state new-state]
  {:state (-> state
              (merge new-state)
              (assoc :view 'new-game-page))})

(defmethod message 'join-failed [_ state]
  {:state (-> state
              (assoc :room-code "")
              (assoc :view 'join-page))})

(defmethod message 'play [_ state new-state]
  {:state (-> state
              (merge new-state)
              (assoc :view 'game))})

(defmethod message :change-view [_ state view]
  {:state (assoc state :view view)})

(defmethod message :new-game [_ state]
  {:state (assoc state :view 'waiting)
   :universe-dispatch ['new-game]})

(defmethod message :copy-room-id [_ state]
  {:state (assoc state :room-id-copied? true)})

(defmethod message :change-room-code [_ state code]
  {:state (assoc state :room-code code)})

(defmethod message :join-room [_ state]
  {:state (assoc state :view 'waiting)
   :universe-dispatch ['join (:room-code state)]})

(defmethod message :change-direction [_ _ direction]
  {:universe-dispatch ['change-direction direction]})
