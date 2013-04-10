(ns udp-tunnel.obfuscator
  (:gen-class)
  (:import java.math.BigInteger)
  (:use clojure.pprint))

(defn rand-bytes
  [n]
  (map (fn [_] (rand-int 255)) (range n)))

(defn md5<-bytes
  "Generate a md5 checksum for the given byte-array."
  [data]
  (let [hash-bytes
        (doto (java.security.MessageDigest/getInstance "MD5")
          (.reset)
          (.update data))]
    (.digest hash-bytes)))

(defn md5
  "Generate a md5 checksum for the given string."
  [token]
  (md5<-bytes (.getBytes token))

(defn md5->hex
  "Generate a md5 checksum for the given string."
  [token]
  (.toString (BigInteger. 1 (md5 token)) 16))

#_(defn map-2step
  "Returns a lazy seq by applying f to the first and second items, then
  the third and fourth, and so on."
  [f coll]
  (assert (even? (count coll)))
  (when-let [x1 (first coll)]
    (when-let [x2 (second coll)]
      (concat (f x1 x2) (map-2step f (drop 2 coll))))))

(defn get-table
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


;; doesn't work
(defn encrypt
  [data table]
  (let [h (seq (md5<-bytes data))
        len (count data)
        pad-len (cond
                  (<= len 50) 150
                  (<= len 100) 100
                  (<= len 150) 50
                  :default 10)
        rnd (rand-bytes pad-len)
        data-seq (seq data)
        data' (concat h [pad-len 0] rnd data-seq)]
    (pprint data')
    (map #(nth table (int %)) data')))






