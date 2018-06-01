### 1. 系统层面
这方面SS给出了非常详尽的优化指南，主要有：优化内核参数，开启TCP Fast Open  
####1.1优化内核参数
编辑```vim /etc/sysctl.conf```   
复制进去   
```
fs.file-max = 51200

net.core.rmem_max = 67108864
net.core.wmem_max = 67108864
net.core.netdev_max_backlog = 250000
net.core.somaxconn = 4096

net.ipv4.tcp_syncookies = 1
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_tw_recycle = 0
net.ipv4.tcp_fin_timeout = 30
net.ipv4.tcp_keepalive_time = 1200
net.ipv4.ip_local_port_range = 10000 65000
net.ipv4.tcp_max_syn_backlog = 8192
net.ipv4.tcp_max_tw_buckets = 5000
net.ipv4.tcp_fastopen = 3
net.ipv4.tcp_mem = 25600 51200 102400
net.ipv4.tcp_rmem = 4096 87380 67108864
net.ipv4.tcp_wmem = 4096 65536 67108864
net.ipv4.tcp_mtu_probing = 1
net.ipv4.tcp_congestion_control = hybla
```
保存生效  
`sysctl -p`  
其中最后的hybla是为高延迟网络（如美国，欧洲）准备的算法，需要内核支持，测试内核是否支持，在终端输入：  
`sysctl net.ipv4.tcp_available_congestion_control`  
如果结果中有hybla，则证明你的内核已开启hybla，如果没有hybla，可以用命令`modprobe tcp_hybla`开启。
  
对于低延迟的网络（如日本，香港等），可以使用`htcp`，可以非常显著的提高速度，首先使用`modprobe tcp_htcp`开启，再将`net.ipv4.tcp_congestion_control = hybla`改为`net.ipv4.tcp_congestion_control = htcp`，建议EC2日本用户使用这个算法。


***

#### 1.2 TCP优化  
1.修改文件句柄数限制  
如果是ubuntu/centos均可修改`/etc/sysctl.conf`  
修改`vim /etc/security/limits.conf`文件，加入  
```
* soft nofile 51200
* hard nofile 51200
```
重启前执行
`ulimit -n 51200`

#### 1.3 BBR
Google 开源了其 TCP BBR 拥塞控制算法，并提交到了 Linux 内核，最新的 4.9 版内核已经用上了该算法。

检查内核版本
`uname -r`
返回
`4.9.4-v7+`
大于4.9即可开启BBR

增加或修改`/etc/sysctl.conf`以下的值
```
net.core.default_qdisc=fq
net.ipv4.tcp_congestion_control=bbr
```
执行`sysctl -p`使配置生效,然后重启服务器

检查是否开启
`sysctl net.ipv4.tcp_available_congestion_control` 返回 `net.ipv4.tcp_available_congestion_control = bbr cubic reno`

`lsmod | grep bbr` 返回 `tcp_bbr 20480 14`



***


#### 1.4 开启TCP Fast Open
这个需要服务器和客户端都是Linux 3.7+的内核，一般Linux的服务器发行版只有debian jessie有3.7+的，客户端用Linux更是珍稀动物，所以这个不多说，如果你的服务器端和客户端都是Linux 3.7+的内核，那就在服务端和客户端的`vim /etc/sysctl.conf`文件中再加上一行。    
```
# turn on TCP Fast Open on both client and server side
net.ipv4.tcp_fastopen = 3
```
然后把`vi /etc/shadowsocks.json`配置文件中"fast_open": false改为"fast_open": true。这样速度也将会有非常显著的提升。

***

### 3. 网络层面
此外，选择合适的端口也能优化梯子的速度，广大SS用户的实践经验表明，检查站（GFW）存在一种机制来降低自身的运算压力，即常用的协议端口（如http，smtp，ssh，https，ftp等）的检查较少，所以建议SS绑定这些常用的端口（如：21，22，25，80，443），速度也会有显著提升。  
如果你还要给小伙伴爬，那我建议开启多个端口而不是共用，这样网络会更加顺畅。  

#### 3.1 防火墙设置（如有）
自动调整MTU  
`iptables -I FORWARD -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --clamp-mss-to-pmtu`  

开启 NAT （记得把 eth0 改成自己的网卡名，openvz 的基本是 venet0 ）  
`iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE`  

开启 IPv4 的转发  
`sysctl -w net.ipv4.ip_forward=1`  

打开 443 端口  
```
iptables -I INPUT -p tcp --dport 443 -j ACCEPT
iptables -I INPUT -p udp --dport 443 -j ACCEPT
```

重启防火墙iptables：  
`service iptables restart`  


***
参考  
https://www.zxc.so/shadowsocks-ladder.html
https://teddysun.com/339.html
https://www.shadowsocks.org/en/config/advanced.html
https://github.com/iMeiji/shadowsocks_install/wiki/shadowsocks-optimize
