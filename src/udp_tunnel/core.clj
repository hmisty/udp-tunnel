(ns udp-tunnel.core
  (:gen-class)
  (:use [udp-tunnel.proxy]))

(def CONFIG_FILE "config.clj")

(def load-config
  (comp read-string slurp))

(defn arg-parse
  [args]
  (loop [opts {}
         x (first args)
         xs (rest args)]
    (cond 
      (nil? x) opts
      (not= (.startsWith x "-")) opts
      (empty? xs) (assoc opts x :true)
      (.startsWith (first xs) "-") (recur (assoc opts x :true) (first xs) (rest xs))
      :default (recur (assoc opts x (first xs)) (second xs) (drop 2 xs)))))

(defn -main [& args]
  "command line args:
  -f CONFIG_FILE. config.clj as default if not specified.
  -s. override the mode as tunnel server.
  -c. override the mode as tunnel client.
  if there's neither -s nor -c, the :mode in CONFIG_FILE decides."
  (let [{config-file "-f" mode-server "-s" mode-client "-c"
         :or {config-file CONFIG_FILE}} (arg-parse args)
        config (load-config config-file)]
    (start-proxy
      (cond
        (true? mode-server) (assoc config :mode :tunnel-server)
        (true? mode-client) (assoc config :mode :tunnel-client)
        :default config))))

