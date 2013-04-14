(ns udp-tunnel.proxy
  (:gen-class)
  (:use [udp-tunnel.obfuscator :only (get-encrypt-table get-decrypt-table encrypt decrypt encrypt-v2 decrypt-v2)]
        [clojure.pprint])
  (:import (java.net InetAddress DatagramPacket DatagramSocket
                     SocketTimeoutException)))

;; tunnnel server and client are the similar proxies
;; they only differentiate from when to encode and when to decode

;; Terms
;; host = host name, e.g. liuconsulting.com or 1.2.3.4
;; addr = host addr, the InetAddress instance

(def BUFFER_SIZE 65535)
(def DEBUG true)

(def active (atom true))
(def client-sockaddr (atom {})) ;; the last seen client socket address

(defmacro debug-info
  [info]
  (if DEBUG `(do (print ~info) (flush)) nil))

(defmacro memorize-client
  [id packet]
  `(when-not (= (@client-sockaddr ~id) (.getSocketAddress ~packet))
     (swap! client-sockaddr assoc-in [~id] (.getSocketAddress ~packet))))

(defmacro recall-client
  [id]
  `(@client-sockaddr ~id))

(declare ^:dynamic *decrypt*)
(declare ^:dynamic *encrypt*)

(defn decrypt-encrypt
  [packet encrypt-table decrypt-table]
  (let [buf (.getData packet)
        offset (.getOffset packet)
        length (.getLength packet)
        data (byte-array (take length (drop offset buf)))
        data' (if decrypt-table (*decrypt* data decrypt-table) data)
        data'' (if encrypt-table (*encrypt* data' encrypt-table) data')]
    (.setData packet data'' 0 (count data''))))

(defn upstream
  [id left-socket right-socket encrypt-table decrypt-table]
  (println (str "upstream " id " started"
                (if decrypt-table " + decrypting in" "")
                (if encrypt-table " + encrypting out" "")))
  (let [buf (byte-array BUFFER_SIZE)
        packet (DatagramPacket. buf BUFFER_SIZE)]
    (loop []
      (when @active
        (try 
          (.receive left-socket packet)
          (memorize-client id packet)
          (decrypt-encrypt packet encrypt-table decrypt-table)
          (.setSocketAddress packet (.getRemoteSocketAddress right-socket))
          (.send right-socket packet)
          (catch SocketTimeoutException e1 (debug-info "?"))
          (catch Exception e (debug-info (str "x" (.getMessage e))))
          #_(finally (debug-info ">")))
        (.setData packet buf 0 BUFFER_SIZE)
        (recur)))))

(defn downstream
  [id left-socket right-socket encrypt-table decrypt-table]
  (println (str "downstream " id " started" 
                (if decrypt-table " + decrypting in" "")
                (if encrypt-table " + encrypting out" "")))
  (let [buf (byte-array BUFFER_SIZE)
        packet (DatagramPacket. buf BUFFER_SIZE)]
    (loop []
      (when @active
        (try 
          (.receive right-socket packet)
          (decrypt-encrypt packet encrypt-table decrypt-table)
          (.setSocketAddress packet (recall-client id))
          (.send left-socket packet)
          (catch SocketTimeoutException e1 (debug-info "?"))
          (catch Exception e (debug-info (str "x" (.getMessage e))))
          #_(finally (debug-info "<")))
        (.setData packet buf 0 BUFFER_SIZE)
        (recur)))))

(defn start-proxy'
  [id mode local remote password timeout]
  (let [[host port] local
        [s-host s-port] remote
        timeout' (* 1000 timeout)
        encrypt-table (get-encrypt-table password)
        decrypt-table (get-decrypt-table encrypt-table)
        up-encrypt (if (= mode :tunnel-client) encrypt-table nil)
        up-decrypt (if (= mode :tunnel-server) decrypt-table nil)
        down-encrypt (if (= mode :tunnel-server) encrypt-table nil)
        down-decrypt (if (= mode :tunnel-client) decrypt-table nil)]
    (let [l-sock (doto (DatagramSocket. port (InetAddress/getByName host))
                   (.setSoTimeout timeout'))
          r-sock (doto (DatagramSocket.) (.setSoTimeout timeout')
                   (.connect (InetAddress/getByName s-host) s-port))]
      (let [up (send-off 
                 (agent [id l-sock r-sock up-encrypt up-decrypt])
                 #(apply upstream %))
            down (send-off 
                   (agent [id l-sock r-sock down-encrypt down-decrypt])
                   #(apply downstream %))]
        [up down]))))

(defn start-proxy
  [config] 
  (let [mode (or (:mode config) :tunnel-client)
        locals (partition 2 (mode config))
        remotes (partition 2 ((if (= mode :tunnel-client) :tunnel-server :server) config))
        password (:password config)
        timeout (:timeout config)
        algorithm (:algorithm config)]
    (println (str "starting " mode))
    (assert (= (count locals) (count remotes)))
    (binding [*decrypt* (if (= algorithm 2) decrypt-v2 decrypt)
              *encrypt* (if (= algorithm 2) encrypt-v2 encrypt)]
      (let [agents (map #(start-proxy' % mode %2 %3 password timeout)
                        (range (count locals)) locals remotes)]
        (apply await (flatten agents))))))

