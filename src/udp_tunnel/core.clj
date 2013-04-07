(ns udp-tunnel.core
  (:gen-class)
  (:use [udp-tunnel.proxy]))

(def CONFIG_FILE "config.clj")

(def load-config
  (comp read-string slurp))

(defn -main [& args]
  (let [config (load-config CONFIG_FILE)]
    (start-proxy config)))

