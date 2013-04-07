(ns udp-tunnel.proxy
  (:import (java.net InetAddress DatagramPacket DatagramSocket)))

;; tunnnel server and client are the similar proxies
;; they only differentiate from when to encode and when to decode

;; Terms
;; host = host name, e.g. liuconsulting.com or 1.2.3.4
;; addr = host addr, the InetAddress instance

(def PACKET_SIZE 1024)
(def DEBUG true)

(def socket-south (ref nil)) ;; socket downstream, to listen
(def socket-north (ref nil)) ;; socket upstream, to connect

(defmacro debug-info
  [info]
  (if DEBUG `(do (print ~info) (flush)) nil))

(defn upstream
  []
  (let [packet (DatagramPacket. (byte-array PACKET_SIZE) PACKET_SIZE)]
    (println "upstream started.")
    (loop []
      (when-not (or (nil? @socket-south) (nil? @socket-north))
        (.receive @socket-south packet)
        (debug-info "^")
        (.setAddress packet (.getInetAddress @socket-north))
        (.setPort packet (.getPort @socket-north))
        (.send @socket-north packet)
        (recur)))
    (println "upstream exit.")))

(defn downstream
  []
  (let [packet (DatagramPacket. (byte-array PACKET_SIZE) PACKET_SIZE)]
    (println "downstream started.")
    (loop []
      (when-not (or (nil? @socket-south) (nil? @socket-north))
        (.receive @socket-north packet)
        (debug-info "v")
        (.setAddress packet (.getInetAddress @socket-south))
        (.setPort packet (.getPort @socket-south))
        (.send @socket-south packet)
        (recur)))
    (println "downstream exit.")))

(declare stop-proxy)

(defn start-proxy
  ([config] 
   (apply start-proxy (map #(% config)
                           [:mode :local :remote :password :timeout])))
  ([mode local remote password timeout]
   (let [[host port] local
         [s-host s-port] remote
         socket-listen (DatagramSocket. port (InetAddress/getByName host))
         socket-connect (doto (DatagramSocket.) 
                          (.connect (InetAddress/getByName s-host) s-port))]
     (dosync
       (ref-set socket-south socket-listen)
       (ref-set socket-north socket-connect))
     (let [up (future (upstream) true)
           down (future (downstream) true)]
       (and @up @down)
       (stop-proxy)))))

(defn stop-proxy
  []
  (.close @socket-south)
  (.close @socket-north)
  (dosync
    (ref-set socket-south nil)
    (ref-set socket-north nil)))

