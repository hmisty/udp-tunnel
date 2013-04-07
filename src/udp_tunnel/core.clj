(ns udp-tunnel.core
  (:use [udp-tunnel.proxy]))

(def CONFIG_FILE "config.clj")

(def load-config
  (comp read-string slurp))

(defn -main [& args]
  (let [config (load-config CONFIG_FILE)]
    (apply start-proxy (map #(% config) [:mode :local :remote :password :timeout]))))

