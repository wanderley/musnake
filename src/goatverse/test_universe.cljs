(ns goatverse.test-universe
  (:require [reagent.core :refer [cursor with-let]])
  (:require-macros [goatverse.test-universe]))

(declare world-dispatch)
(defn universe-dispatch [state settings type & params]
  (let [curr     (:universe state)
        res      (apply (get-in settings [:universe :on-message])
                        (into [type curr] params))
        next     (or (:state res) curr)
        messages (:world-messages res)]
    (if messages
      (reduce (fn [state [world-id type & params]]
                (world-dispatch state settings world-id type params))
              (assoc state :universe next)
              messages)
      (assoc state :universe next))))

(defn world-dispatch [snapshot settings world-id type params]
  (let [curr     (get-in snapshot [:worlds world-id])
        res      (apply (get-in settings [:world :on-message])
                        (into [type curr] params))
        next     (or (:state res) curr)
        messages (:universe-dispatch res)]
    (if messages
      (apply universe-dispatch
             (into [(assoc-in snapshot [:worlds world-id] next)
                    settings
                    (first messages)
                    world-id]
                   (rest messages)))
      (assoc-in snapshot [:worlds world-id] next))))

(defmulti step (fn [_ _ command & _] command))
(defmethod step :connect [state settings _ world-id]
  (-> state
      (assoc-in [:worlds world-id]
                (get-in settings [:world :initial-state]))
      (universe-dispatch settings 'connect world-id)))

(defmethod step :dispatch [state settings _ world-id type & params]
  (let [resolve-params (map (fn [p] (if (fn? p) (p state) p)) params)]
    (world-dispatch state settings world-id type resolve-params)))

(defmethod step :tick [state settings]
  (when (-> settings :universe :tick-rate)
    (universe-dispatch state settings 'tick)))

(defn render-reality [reality settings]
  [:div
   (map (fn [[world-id world-state]]
          ^{:key world-id}
          [:div [:h3 world-id]
           [(get-in settings [:world :on-render]) world-state
            (fn [type & params]
              (swap! reality world-dispatch settings world-id type params))]])
        (:worlds @reality))])

(defn render-snapshot [state steps snapshots settings]
  (with-let [change-step (fn [state index]
                           (-> state
                               (assoc :index index)
                               (assoc :reality (get snapshots index))))
             reality (cursor state [:reality])
             tick-fn (js/setInterval #(when (:live? @reality)
                                        (swap! reality
                                               universe-dispatch settings 'tick))
                                     (* 1000
                                        (get-in settings [:universe :tick-rate])))]
    [:div
     [:h2
      (let [idx (:index @state)
            step (get steps (dec idx))]
        (if (zero? idx)
          "Before the creation ..."
          (str (str idx) ": " (str step))))]
     [:button {:on-click
               (fn [_]
                 (swap! state
                        #(change-step % (max 0 (-> % :index dec)))))}
      "Prev"]
     " "
     [:button {:on-click #(swap! reality
                                 universe-dispatch settings 'tick)} "Tick"]
     " "
     [:button {:on-click
               (fn [_]
                 (swap! state #(change-step % (min (-> snapshots count)
                                                   (-> % :index inc)))))}
      "Next"]
     " Live?"
     [:input {:type "checkbox"
              :value (or (:live? @reality) false)
              :on-click #(swap! reality update :live? (fn [live?] (not live?)))}]
     [render-reality reality settings]]
    (finally (js/clearInterval tick-fn))))


(defn steps->snapshots [steps settings]
  (reduce (fn [res cmd]
            (conj res (apply step (into [(last res) settings] cmd))))
          [{:universe (get-in settings [:universe :initial-state])
            :worlds {}}]
          steps))

(defn simulate-universe [settings & steps]
  (last (steps->snapshots steps settings)))
