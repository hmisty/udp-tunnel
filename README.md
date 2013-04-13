# udp-tunnel

A lightweight udp tunneling client and server with obfuscated packets in-between.

## Quick Start

(make sure you have java first)

        git clone https://github.com/hmisty/udp-tunnel.git
        cd udp-tunnel

If you already have lein in your PATH:

        lein uberjar

If you don't have lein:

        curl -O https://raw.github.com/technomancy/leiningen/stable/bin/lein
        chmod +x lein
        ./lein uberjar

Done! Let's play with it. Open a shell window:

	java -jar target/udp-tunnel-*-standalone.jar

Open anther shell:

	java -jar target/udp-tunnel-*-standalone.jar -s

Now two tunnels has been established in your local machine 127.0.0.1:

	:8000 <-> :9000 <-> :10000
	:8001 <-> :9001 <-> :10001

Let's start two udp servers listening :10000 and :10001 in two extra shells:

1st server:

	nc -u -l :10000

2nd server:

	nc -u -l :10001

And finally open a new shell window, run:

	nc -u 127.0.0.1 8000 
	
and see it can communicate with the 1st server bi-directionally via the tunnel.
Then you can stop it and connect to the 2nd server to have more fun:

	nc -u 127.0.0.1 8001

## Configurations

The default configuration file is the config.clj located in the current directory.

You can have the separate config files for the tunnel client and server respectively, as is in config-example1.clj:

For tunnel client:

	;; client <-> tunnel-client x.y.z.w:53 <-> tunnel-server 1.2.3.4:9999
	{:mode              :tunnel-client
	:tunnel-client     ["127.0.0.1" 53]
	:tunnel-server     ["1.2.3.4" 9999]
	:password          "h1m@i3s$t5y^"
	:timeout           600}

For tunnel server:

	;; tunnel-client <-> tunnel-server 1.2.3.4:9999 <-> server 8.8.8.8:53
	{:mode              :tunnel-server
	:tunnel-server     ["1.2.3.4" 9999]
	:server            ["8.8.8.8" 53]
	:password          "h1m@i3s$t5y^"
	:timeout           600}

Or you can have the same config file for both tunnel client and server, as shown in config-example2.clj:

	{:tunnel-client   ["127.0.0.1" 53 "127.0.0.1" 9999]
	:tunnel-server   ["1.2.3.4" 50001 "1.2.3.4" 50000]
	:server          ["8.8.8.8" 53 "127.0.0.1" 9999]
	:password        "h1m@i3s$t5y^"
	:timeout         600}

udp-tunnel does support creating multiple tunnels as seen above.

In such a case, you need to use command line parameter to advise the program to play the role of tunnel client or tunnel server.

## Command Line Parameters

You can specify command line parameters explicitly to override the default settings or the settings read from the config file. Several parameters are supported:

	-f CONFIG\_FILE : load the config file CONFIG\_FILE instead of config.clj
	-s : run as the tunnel server
	-c : run as the tunnel client (default)

## Under the Hood

* programming language: [clojure][1]
* build tool: [leiningen][2]

[1]: http://clojure.org/
[2]: http://leiningen.org/

## Algorithm

The obfuscation algorithm is the re-implementaiton of that in [shadowsocks][3] using clojure.

[3]: https://github.com/clowwindy/shadowsocks

## Shortcomings

For single client single user only.

## License

Copyright © 2013 Evan Liu (hmisty).

Distributed under the Eclipse Public License, the same as Clojure.
