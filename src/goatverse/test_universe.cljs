(ns goatverse.test-universe
  (:require [reagent.core :refer [cursor with-let]])
  (:require-macros [goatverse.test-universe]))

(defn universe->world-dispatch
  "Send message from universe to a specific world."
  [snapshot settings client-id type params]
  (assoc-in snapshot [:worlds client-id]
            (apply (get-in settings [:world :on-server-message])
                   (into [type (get-in snapshot [:worlds client-id])]
                         params))))

(defn universe-dispatch [state settings type & params]
  (let [curr     (:universe state)
        res      (apply (get-in settings [:universe :on-message])
                        (into [type curr] params))
        next     (or (:state res) curr)
        messages (:client-messages res)]
    (if messages
      (reduce (fn [state [client-id type & params]]
                (universe->world-dispatch state settings client-id type params))
              (assoc state :universe next)
              messages)
      (assoc state :universe next))))

(defn world-dispatch [snapshot settings client-id type params]
  (let [curr     (get-in snapshot [:worlds client-id])
        res      (apply (get-in settings [:world :on-message])
                        (into [type curr] params))
        next     (or (:state res) curr)
        messages (:server-dispatch res)]
    (if messages
      (apply universe-dispatch
             (into [(assoc-in snapshot [:worlds client-id] next)
                    settings
                    (first messages)
                    client-id]
                   (rest messages)))
      (assoc-in snapshot [:worlds client-id] next))))

(defmulti step (fn [_ _ command & _] command))
(defmethod step :connect [state settings _ client-id]
  (-> state
      (assoc-in [:worlds client-id]
                (get-in settings [:world :initial-state]))
      (universe-dispatch settings 'connect client-id)))

(defmethod step :dispatch [state settings _ client-id type & params]
  (let [resolve-params (map (fn [p] (if (fn? p) (p state) p)) params)]
    (world-dispatch state settings client-id type resolve-params)))

(defmethod step :tick [state settings]
  (when (-> settings :universe :tick-rate)
    (universe-dispatch state settings 'tick)))

(defn render-reality [reality settings]
  [:div
   (map (fn [[client-id world-state]]
          ^{:key client-id}
          [:div [:h3 client-id]
           [(get-in settings [:world :on-render]) world-state
            (fn [type & params]
              (swap! reality world-dispatch settings client-id type params))]])
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
              :checked (:live? reality)
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
