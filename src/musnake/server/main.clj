(ns musnake.server.main
  (:require [musnake.server.handler :as h]
            [org.httpkit.server :as hk])
  (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [port (if (empty? args) 8080 (or (Integer. (first args)) 8080))]
    (println (str "Starting server on http://localhost:" port))
    (hk/run-server h/app {:port port})
    (def tic! (future (while true
                        (do (Thread/sleep 100)
                            (h/toc!)))))))
