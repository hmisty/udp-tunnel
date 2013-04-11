(ns udp-tunnel.proxy
  (:gen-class)
  (:use [udp-tunnel.obfuscator :only (get-encrypt-table get-decrypt-table encrypt decrypt)]
        [clojure.pprint])
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

(defn decrypt-encrypt
  [packet encrypt-table decrypt-table]
  (let [buf (.getData packet)
        offset (.getOffset packet)
        length (.getLength packet)
        data (byte-array (take length (drop offset buf)))
        data' (if decrypt-table (decrypt data decrypt-table) data)
        data'' (if encrypt-table (encrypt data' encrypt-table) data')]
    (.setData packet data'')))

(defn upstream
  [left-socket right-socket encrypt-table decrypt-table]
  (println "upstream started.")
  (if decrypt-table (println "upstream decrypting in."))
  (if encrypt-table (println "upstream encrypting out."))
  (let [packet (DatagramPacket. (byte-array PACKET_SIZE) PACKET_SIZE)]
    (loop []
      (when @active
        (try 
          (.receive left-socket packet)
          (remember packet)
          (decrypt-encrypt packet encrypt-table decrypt-table)
          (.setSocketAddress packet (.getRemoteSocketAddress right-socket))
          (.send right-socket packet)
          (catch SocketTimeoutException e1 (debug-info "?"))
          (catch Exception e (debug-info "x"))
          (finally (debug-info ">")))
        (recur)))))

(defn downstream
  [left-socket right-socket encrypt-table decrypt-table]
  (println "downstream started.")
  (if decrypt-table (println "downstream decrypting in."))
  (if encrypt-table (println "downstream encrypting out."))
  (let [packet (DatagramPacket. (byte-array PACKET_SIZE) PACKET_SIZE)]
    (loop []
      (when @active
        (try 
          (.receive right-socket packet)
          (decrypt-encrypt packet encrypt-table decrypt-table)
          (.setSocketAddress packet @client-sockaddr)
          (.send left-socket packet)
          (catch SocketTimeoutException e1 (debug-info "?"))
          (catch Exception e (debug-info "x"))
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
         timeout' (* 1000 timeout)
         encrypt-table (get-encrypt-table password)
         decrypt-table (get-decrypt-table encrypt-table)
         upstream-encrypt (if (= mode :tunnel-client) encrypt-table nil)
         upstream-decrypt (if (= mode :tunnel-server) decrypt-table nil)
         downstream-encrypt (if (= mode :tunnel-server) encrypt-table nil)
         downstream-decrypt (if (= mode :tunnel-client) decrypt-table nil)]
     (with-open [left-socket (doto (DatagramSocket.
                                       port
                                       (InetAddress/getByName host))
                                 (.setSoTimeout timeout'))
                 right-socket (doto (DatagramSocket.) 
                                 (.setSoTimeout timeout')
                                 (.connect (InetAddress/getByName s-host) s-port))]
       (let [up (future (upstream left-socket right-socket 
                                  upstream-encrypt upstream-decrypt) true)
             down (future (downstream left-socket right-socket 
                                      downstream-encrypt downstream-decrypt) true)]
         (and @up @down))))))

