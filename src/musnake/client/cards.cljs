(ns musnake.client.cards
  (:require [cljs.test :refer [is testing] :include-macros true]
            [devcards.core :refer [deftest]]
            [musnake.client.events :as c]
            [musnake.client.test-universe :refer [defcard-universe simulate-universe]]
            [musnake.client.views :refer [game-view]]
            [musnake.server.events :as s]
            [musnake.shared.model :as m]))

(defonce settings
  {:universe {:initial-state m/server-initial-state
              :tick-rate (/ 1 4)
              :on-message s/event!}
   :world {:initial-state m/client-initial-state
           :on-render game-view
           :on-message c/message
           :on-universe-message c/server-message}})

(defcard-universe two-clients-on-same-room
  settings
  [:connect :snake-a]
  [:connect :snake-b]
  [:dispatch :snake-a :new-game]
  [:dispatch :snake-a :change-view 'game]
  [:dispatch :snake-b :join-room]
  [:dispatch :snake-b :change-room-code
   #(-> :worlds % :snake-a :room-id)]
  [:dispatch :snake-b :join-room]
  [:dispatch :snake-a :change-direction 'down]
  [:dispatch :snake-b :change-direction 'up]
  [:tick])

(deftest test-musnake
  (testing "Users can play on lobby"
    (let [s (simulate-universe
             settings
             [:connect :snake-a]
             [:dispatch :snake-a :change-view 'game]
             [:tick])]
      (is (= (-> s (get-in [:universe :client-rooms]) keys set)
             (-> s (get-in [:universe :rooms :lobby :snakes]) keys set)
             (-> s (get-in [:worlds :snake-a :snakes]) keys set)
             #{:snake-a})
          "One snake is playing alone on the lobby!"))

    (let [s (simulate-universe
             settings
             [:connect :snake-a]
             [:connect :snake-b]
             [:dispatch :snake-a :change-view 'game]
             [:dispatch :snake-b :change-view 'game]
             [:tick])]
      (is (= (-> s (get-in [:universe :client-rooms]) keys set)
             (-> s (get-in [:universe :rooms :lobby :snakes]) keys set)
             (-> s (get-in [:worlds :snake-a :snakes]) keys set)
             (-> s (get-in [:worlds :snake-b :snakes]) keys set)
             #{:snake-a :snake-b})
          "Both snakes are playing on lobby")))

  (testing "Users can play on private room"
    (let [s (simulate-universe
             settings
             [:connect :snake-a]
             [:connect :snake-b]
             [:dispatch :snake-a :new-game]
             [:dispatch :snake-a :change-view 'game]
             [:dispatch :snake-b :join-room]
             [:dispatch :snake-b :change-room-code
              #(-> % :worlds :snake-a :room-id)]
             [:dispatch :snake-b :join-room]
             [:tick])
          snake-a-room-id (get-in s [:universe :client-rooms :snake-a])
          snake-a-room (get-in s [:universe :rooms snake-a-room-id])]

      (is (zero? (-> s (get-in [:universe :rooms :looby]) count))
          "No one is in the lobby")

      (is (= (-> s (get-in [:universe :client-rooms]) keys set)
             (-> snake-a-room :snakes keys set)
             (-> s (get-in [:worlds :snake-a :snakes]) keys set)
             (-> s (get-in [:worlds :snake-b :snakes]) keys set)
             #{:snake-a :snake-b})))))
