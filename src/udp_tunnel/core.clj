(ns udp-tunnel.core
  (:use [udp-tunnel.proxy]))

(def CONFIG_FILE "config.clj")

(def load-config
  (comp read-string slurp))

(defn -main [& args]
  (let [config (load-config CONFIG_FILE)]
    (cond
      (= :client (:mode config)) (start-proxy)
      (= :server (:mode config)) (start-proxy)
      :else (println "error: :mode is neither :client nor :server."))))

