;; client <-> tunnel-client x.y.z.w:53 <-> tunnel-server 1.2.3.4:9999
{:mode              :tunnel-client
 :tunnel-client     ["127.0.0.1" 53]
 :tunnel-server     ["1.2.3.4" 9999]
 :password          "h1m@i3s$t5y^"
 :timeout           60}

;; tunnel-client <-> tunnel-server 1.2.3.4:9999 <-> server 8.8.8.8:53
#_{:mode              :tunnel-server
   :tunnel-server     ["1.2.3.4" 9999]
   :server            ["8.8.8.8" 53]
   :password          "h1m@i3s$t5y^"
   :timeout           60}

