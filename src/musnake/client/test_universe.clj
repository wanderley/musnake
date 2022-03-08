(ns musnake.client.test-universe)

(defmacro defcard-universe [name settings & steps]
  `(let [snapshots# (steps->snapshots [~@steps] ~settings)
         reality# (first snapshots#)]
     (devcards.core/defcard-rg ~name
       (fn [state# _#]
         [render-snapshot state# snapshots# ~settings])
       (reagent.core/atom
        {:index 0
         :steps [~@steps]
         :reality reality#})
       {:inspect-data true
        :history true})))
