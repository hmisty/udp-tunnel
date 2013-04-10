(ns udp-tunnel.obfuscator
  (:gen-class))

(defn rand-bytes
  [n]
  (map (fn [_] (rand-int 255)) (range n)))

(defn md5
  "Generate a md5 checksum for the given string."
  [token]
  (let [hash-bytes
        (doto (java.security.MessageDigest/getInstance "MD5")
          (.reset)
          (.update (.getBytes token)))]
    (.digest hash-bytes)))

(defn md5-hex
  "Generate a md5 checksum for the given string."
  [token]
  (.toString
    (new java.math.BigInteger 1 (md5 token))
    16))


