(ns udp-tunnel.obfuscator
  (:gen-class)
  (:import java.math.BigInteger)
  (:use clojure.pprint))

(set! *warn-on-reflection* true)

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
  [^String token]
  (md5-bytes (.getBytes token)))

(defn md5-bytes
  "Generate a md5 checksum (byte-array) for the given byte-array."
  [^bytes data]
  (let [hash-bytes
        (doto (java.security.MessageDigest/getInstance "MD5")
          (.reset)
          (.update data))]
    (.digest hash-bytes)))

(defn md5-hex
  "Generate a md5 checksum (hex) for the given byte-array."
  [data]
  (.toString (BigInteger. (int 1) ^bytes (md5 data)) 16))

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
        (byte-array (map signed-byte table))))))

(defn get-decrypt-table
  [^bytes encrypt-table]
  (byte-array (map signed-byte (sort #(compare (unsigned-byte (aget encrypt-table %)) (unsigned-byte (aget encrypt-table %2))) (range 256)))))

#_(defn encrypt
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

#_(defn decrypt
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

(defn encrypt
  "Returns encrypted byte-array of the given data (byte-array)."
  [^bytes data ^bytes encrypt-table]
  (let [m (md5-seq data) ;; 16 bytes
        len (alength data)
        pad-len (cond
                  (<= len 50) 150
                  (<= len 100) 100
                  (<= len 150) 50
                  :default 10)
        pad-len-a (+ pad-len (- 3 (rand-int 7)))
        pad-len-b (rand-int 3)
        rnd (rand-bytes (+ pad-len-a pad-len-b))
        data-seq (unsigned-seq data)
        data' (concat m [pad-len-a pad-len-b] rnd data-seq)]
    (byte-array (map #(signed-byte (aget encrypt-table %)) data'))))

(defn decrypt
  "Returns decrypted byte-array of the given data (byte-array)."
  [^bytes data ^bytes decrypt-table]
  (assert (>= (alength data) 150))
  (let [data' (map #(unsigned-byte (aget decrypt-table %)) (unsigned-seq data))
        m (take 16 data')
        pad-len-a (nth data' 16)
        pad-len-b (nth data' 17)
        pad-len (+ pad-len-a pad-len-b)
        origin (drop (+ 18 pad-len) data')
        origin' (byte-array (map signed-byte origin))
        m' (md5-seq origin')]
    (assert (= m m'))
    origin'))

(defn move!
  [array length offset new-offset f]
  (cond
    (> new-offset offset) 
    (dotimes [i ^int length]
      (let [j ^int (unchecked-subtract (unchecked-subtract length i) 1)
            from ^int (unchecked-add offset j)
            to ^int (unchecked-add new-offset j)]
        (aset ^bytes array to ^byte (f (aget ^bytes array from)))))
    (< new-offset offset) 
    (dotimes [i ^int length] 
      (let [from ^int (unchecked-add offset i)
            to ^int (unchecked-add new-offset i)]
        (aset ^bytes array to ^byte (f (aget ^bytes array from)))))
    :default ;; new-offset = offset
    (dotimes [i ^int length] 
      (let [idx ^int (unchecked-add offset i)]
        (aset ^bytes array idx ^byte (f (aget ^bytes array idx)))))))

(defn encrypt!
  "Encrypts the array with the given length and offset, and
  returns [new-array new-length new-offset]."
  [^bytes array ^long length ^long offset ^bytes encrypt-table]
  (let [m nil ;;(md5-bytes array) ;; 16 bytes TODO
        pad-len' (cond
                  (<= length 50) 150
                  (<= length 100) 100
                  (<= length 150) 50
                  :default 10)
        pad-len-a (unchecked-add pad-len' (unchecked-subtract 3 (rand-int 7)))
        pad-len-b (rand-int 3)
        pad-len (unchecked-add pad-len-a pad-len-b)

        ;; head structure: <MD5:16B><pad-len-a:1B><pad-len-b:1B><rnd:pad-len-a+b>
        head-len (unchecked-add 18 pad-len) ;; 18 = 16 + 2

        ;; encrypting functions
        f' #(aget encrypt-table %) 
        f #(aget encrypt-table (unsigned-byte %))]

    (move! array length offset head-len f) ;; move the body to leave room for head
    ;;(dotimes [i 16] (aset array i (f (aget m i)))) ;; fill in the <MD5:16B>
    (dotimes [i 16] (aset array i ^byte (f 0))) ;; fill in the <MD5:16B>
    (aset array 16 ^byte (f' pad-len-a))
    (aset array 17 ^byte (f' pad-len-b)) ;; 17 = 16 + 1
    (dotimes [i pad-len]
      (aset array (unchecked-add 18 i) ;; 18 = 16 + 2
            ^byte (f' (rand-int 255)))) ;; fill in rand bytes
    [array (unchecked-add head-len length) 0]))

(defn decrypt!
  "Encrypts the array with the given length and offset, and
  returns [new-array new-length new-offset]."
  [^bytes array ^long length ^long offset ^bytes decrypt-table]
  (assert (> length 150))
  (let [f' #(aget decrypt-table (unsigned-byte %))]
    (move! array length offset 0 f'))
  (let [;; 16 = len of <MD5:16>
        m nil #_(java.util.Arrays/copyOfRange array 0 16) ;; TODO
        pad-len-a (unsigned-byte (aget array 16))
        pad-len-b (unsigned-byte (aget array 17))
        pad-len (unchecked-add pad-len-a pad-len-b)
        head-len (unchecked-add 18 pad-len)
        data-len (unchecked-subtract length head-len)]
    (move! array data-len head-len 0 identity)
    #_(assert (= m m')) ;; TODO verfiy md5 checksum
    [array data-len 0]))

(comment
  (def ent (get-encrypt-table "foobar"))
  (def det (get-decrypt-table ent))

  (def array (byte-array 1000))
  (def input (byte-array (for [i (range 100)] (signed-byte (rand-int 255)))))
  (dotimes [i 100] (aset array i (aget input i)))

  (def encrypted (encrypt! array 100 0 ent))

  (def decrypted (decrypt! (first encrypted) (second encrypted) 0 det))
  )

(defn burn-test1
  []
  (let [en-tab (get-encrypt-table "a1s@d3f$g5H6J7K*L9:0")
        de-tab (get-decrypt-table en-tab)
        input (byte-array (for [i (range 500)] (signed-byte (rand-int 255))))
        output (encrypt input en-tab)
        n 100]
    (dotimes [i n] (encrypt input en-tab))
    (dotimes [i n] (decrypt output de-tab))))

(defn burn-test2
  []
  (let [ent (get-encrypt-table "a1s@d3f$g5H6J7K*L9:0")
        det (get-decrypt-table ent)
        input (byte-array (for [i (range 2000)] (signed-byte (rand-int 255))))
        [output, len, off] (encrypt! input 500 0 ent)
        n 100]
    (dotimes [i n] (encrypt! input 500 0 ent))
    (dotimes [i n] (decrypt! output len off det))))
