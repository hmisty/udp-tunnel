;; consumer <-> client x.y.z.w:53 <-> server 1.2.3.4:9999
{:mode      :client
 :local     ["127.0.0.1" "53"]
 :remote    ["1.2.3.4" "9999"]
 :password  "h1m@i3s$t5y^"
 :timeout   600}

;; client <-> server 1.2.3.4:9999 <-> service 8.8.8.8:53
#_{:mode      :server
   :local     ["1.2.3.4" "9999"]
   :remote    ["8.8.8.8" "53"]
   :password  "h1m@i3s$t5y^"
   :timeout   600}

