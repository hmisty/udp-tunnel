(ns udp-tunnel.proxy
  (:gen-class)
  (:import (java.net InetAddress DatagramPacket DatagramSocket
                     SocketTimeoutException)))

;; tunnnel server and client are the similar proxies
;; they only differentiate from when to encode and when to decode

;; Terms
;; host = host name, e.g. liuconsulting.com or 1.2.3.4
;; addr = host addr, the InetAddress instance

(def PACKET_SIZE 65535)
(def DEBUG true)

(def active (atom true))
(def client-sockaddr (atom nil)) ;; the last seen client socket address

(defmacro debug-info
  [info]
  (if DEBUG `(do (print ~info) (flush)) nil))

(defmacro remember
  [packet]
  `(when-not (= @client-sockaddr (.getSocketAddress ~packet))
     (swap! client-sockaddr (fn [a#] (.getSocketAddress ~packet)))))

(defn upstream
  [left-socket right-socket]
  (println "upstream started.")
  (let [packet (DatagramPacket. (byte-array PACKET_SIZE) PACKET_SIZE)]
    (loop []
      (when @active
        (try 
          (.receive left-socket packet)
          (remember packet)
          (.setSocketAddress packet (.getRemoteSocketAddress right-socket))
          (.send right-socket packet)
          (catch SocketTimeoutException e1 (debug-info "?"))
          (catch Exception e (debug-info "x"))
          (finally (debug-info ">")))
        (recur)))))

(defn downstream
  [left-socket right-socket]
  (println "downstream started.")
  (let [packet (DatagramPacket. (byte-array PACKET_SIZE) PACKET_SIZE)]
    (loop []
      (when @active
        (try 
          (.receive right-socket packet)
          (.setSocketAddress packet @client-sockaddr)
          (.send left-socket packet)
          (catch SocketTimeoutException e1 (debug-info "?"))
          (catch Exception e (debug-info (str "x" (.getMessage e))))
          (finally (debug-info "<")))
        (recur)))))

(defn start-proxy
  ([config] 
   (apply start-proxy 
          (map #(% config)
               (if (= (:mode config) :tunnel-server)
                 [:mode :tunnel-server :server :password :timeout]
                 [:mode :tunnel-client :tunnel-server :password :timeout]))))
  ([mode local remote password timeout]
   (let [[host port] local
         [s-host s-port] remote
         timeout' (* 1000 timeout)]
     (with-open [left-socket (doto (DatagramSocket.
                                       port
                                       (InetAddress/getByName host))
                                 (.setSoTimeout timeout'))
                 right-socket (doto (DatagramSocket.) 
                                 (.setSoTimeout timeout')
                                 (.connect (InetAddress/getByName s-host) s-port))]
       (let [up (future (upstream left-socket right-socket) true)
             down (future (downstream left-socket right-socket) true)]
         (and @up @down))))))

