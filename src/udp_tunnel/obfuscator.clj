(ns udp-tunnel.obfuscator
  (:gen-class)
  (:import java.math.BigInteger)
  (:use clojure.pprint))

(defn rand-bytes
  [n]
  (map (fn [_] (rand-int 255)) (range n)))

(defn signed-byte
  "Returns a signed byte -128 ~ 127 inclusive of the given number n."
  [n]
  (let [low7bits (bit-and n 0x7f)
        neg (bit-test n 7)
        value (if neg (- low7bits 128) low7bits)]
    (byte value)))

(defn unsigned-byte
  "Returns an unsigned byte (int) 0 ~ 255 inclusive of the given byte b."
  [b]
  (bit-and b 0xff))

(defn unsigned-seq
  "Get a lazy seq of unsigned int from the given byte-array."
  [data]
  (map unsigned-byte data))

(declare md5-bytes)

(defn md5
  "Generate a md5 checksum (byte-array) for the given string."
  [token]
  (md5-bytes (.getBytes token)))

(defn md5-bytes
  "Generate a md5 checksum (byte-array) for the given byte-array."
  [data]
  (let [hash-bytes
        (doto (java.security.MessageDigest/getInstance "MD5")
          (.reset)
          (.update data))]
    (.digest hash-bytes)))

(defn md5-hex
  "Generate a md5 checksum (hex) for the given byte-array."
  [data]
  (.toString (BigInteger. 1 (md5 data)) 16))

(defn md5-seq
  "Generate a md5 checksum (seq) for the given byte-array."
  [data]
  (unsigned-seq (md5-bytes data)))

#_(defn map-2step
  "Returns a lazy seq by applying f to the first and second items, then
  the third and fourth, and so on."
  [f coll]
  (assert (even? (count coll)))
  (when-let [x1 (first coll)]
    (when-let [x2 (second coll)]
      (concat (f x1 x2) (map-2step f (drop 2 coll))))))

(defn get-encrypt-table
  [token]
  (let [s (md5 token)
        ;; take first 8 bytes as little endian so we need reverse
        a (reverse (take 8 s))
        b (reverse (drop 8 s))
        a' (BigInteger. 1 (byte-array a)) ;; BigInteger uses big endian
        b' (BigInteger. 1 (byte-array b))
        table (range 256)]
    (loop [table (range 256)
           i 1
           cmp (fn [x y] (compare (rem a' (+ x i)) (rem a' (+ y i))))]
      (if (< i 1024)
        (recur
          (sort cmp table)
          (+ i 1)
          (fn [x y] (compare (rem a' (+ x i 1)) (rem a' (+ y i 1)))))
        table))))

(defn get-decrypt-table
  [encrypt-table]
  (sort #(compare (nth encrypt-table %) (nth encrypt-table %2)) (range 256)))

(defn encrypt
  "Returns encrypted byte-array of the given data (byte-array)."
  [data encrypt-table]
  (let [m (md5-seq data) ;; 16 bytes
        len (count data)
        pad-len (cond
                  (<= len 50) 150
                  (<= len 100) 100
                  (<= len 150) 50
                  :default 10)
        rnd (rand-bytes pad-len)
        data-seq (unsigned-seq data)
        data' (concat m [pad-len 0] rnd data-seq)]
    (byte-array (map #(signed-byte (nth encrypt-table %)) data'))))

(defn decrypt
  "Returns decrypted byte-array of the given data (byte-array)."
  [data decrypt-table]
  (let [len (count data)]
    (if (< len 150)
      nil
      (let [data' (map #(nth decrypt-table %) (unsigned-seq data))
            m (take 16 data')
            pad-len (nth data' 16)
            origin (drop (+ 18 pad-len) data')
            origin' (byte-array (map signed-byte origin))
            m' (md5-seq origin')]
        (if (= m m') origin' nil)))))

