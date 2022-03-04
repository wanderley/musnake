(ns musnake.client.events
  (:require [musnake.shared.model :as m]))

;;; Server

(defmulti server-message (fn [type & _] type))

(defmethod server-message 'state [_ state new-state]
  (merge state new-state))

(defmethod server-message 'client-id [_ state client-id]
  (assoc state :client-id client-id))

(defmethod server-message 'join-room [_ state new-state]
  (-> state
      (merge new-state)
      (assoc :view 'new-game-page)))

(defmethod server-message 'join-failed [_ state]
  (-> state
      (assoc :room-code "")
      (assoc :view 'join-page)))

(defmethod server-message 'play [_ state new-state]
  (-> state
      (merge new-state)
      (assoc :view 'game)))

;;; Views

(defmulti message (fn [type & _] type))

(defmethod message :change-view [_ state view]
  {:state (assoc state :view view)})

(defmethod message :new-game [_ state]
  {:state (assoc state :view 'waiting)
   :server-dispatch ['new-game]})

(defmethod message :copy-room-id [_ state]
  {:state (assoc state :room-id-copied? true)})

(defmethod message :change-room-code [_ state code]
  {:state (assoc state :room-code code)})

(defmethod message :join-room [_ state]
  {:state (assoc state :view 'waiting)
   :server-dispatch ['join (:room-code state)]})

(defmethod message :change-direction [_ state direction]
  {:server-dispatch ['change-direction direction]})
