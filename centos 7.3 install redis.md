### 先从redis下载源码解压
```
cd /tmp
wget http://download.redis.io/releases/redis-{version}.tar.gz
tar zxf redis-{version}.tar.gz
```  
### 安装依赖
```
yum update -y
yum -y install gcc gcc-c++ jemalloc
```

### 安装redis
```
cd redis-{version}
make
#make PREFIX=/opt/redis 指定安装目录
```

### test
```
cd src
./redis-server
```

### 设置成service 开机启动
```
cp /tmp/redis-{version}/utils/redis_init_script /etc/init.d/redis
```
配置脚本EXEC,CLIEXEC,PIDFILE,CONF这三个变量  
将daemonize改为yes  
并且设置 redis服务运行级别和启动优先级,在文件第二行增加
```
# chkconfig: 2345 90 10
```
设置为开机启动
```
systemctl enable redis
```

