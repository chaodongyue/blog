### 输出计数器
jcmd \<pid\> PerfCounter.print 等于 jstat -snap \<pid\>

### 用python转换成json格式,每10秒收集一次
```python
#!/usr/bin/python3
import json
import os
import time


# 转换成json并保存

# 转换数据类型
def convert_data_type(val):
    item_index = val.find("\"")
    if item_index == -1:
        try:
            val = int(val)
        except ValueError:
            try:
                val = float(val)
            except ValueError:
                val = val
    else:
        val = val.replace("\"", "")
    return val


# 转换字段层级
def key_handler(key):
    append_total = ["sun.cls.appClassLoadTime", "sun.cls.defineAppClassTime", "sun.cls.classVerifyTime",
                    "sun.cls.classLinkedTime", "sun.cls.parseClassTime", "sun.cls.classInitTime"]
    if append_total.count(key) > 0:
        key += ".total"
    return key


# 转换成json并保存
def append_to_file(new_str, output_file):
    index = new_str.index("\n")
    new_str = new_str[index + 1:-1]
    param_list = new_str.splitlines()
    json_obj = {}
    for item in param_list:
        item_list = item.split("=")
        val = item_list[1]
        val = convert_data_type(val)
        key = item_list[0]
        key = key_handler(key)
        json_obj[key] = val
    json_str = json.dumps(json_obj)
    f = open(output_file, "a")
    f.write(json_str + "\n")
    f.close()


def read_source_data(app_pid):
    cmd = "jcmd " + app_pid + " PerfCounter.print"
    pc_str = os.popen(cmd).read()
    return pc_str


def main():
    append_file = "/opt/data/perfcounter.json"
    app_pid_path = "/opt/data/app.pid"

    f = open(app_pid_path, "r")
    app_pid = f.read().replace('\n', '')

    while True:
        pc_str = read_source_data(app_pid)
        append_to_file(pc_str, append_file)
        time.sleep(10)


main()
```
### 输出属性参考
```
// Total time spent on JIT compilation
java.ci.totalTime = 1664764413

// Number of successfully compiled methods
sun.ci.totalCompiles = 66267

// Number of failed compilations
sun.ci.totalBailouts = 1

// Cumulative number of threads ever started
java.threads.started = 2417

// Total time spent in ClassLoader.findClass
sun.classloader.findClassTime = 26643927072

// Total bytes loaded by non-bootstrap ClassLoaders
sun.cls.appClassBytes = 152811197

// Time spent on loading classes by non-bootstrap ClassLoaders
sun.cls.appClassLoadTime = 86351166

// Total time spent on class initialization
sun.cls.classInitTime = 26259674

// Cumulative number of unloaded classes
java.cls.unloadedClasses = 5816

// Distribution of young generation by object age (age = number of survived collections)
sun.gc.generation.0.agetable.*

// Young GC collection count
sun.gc.collector.0.invocations = 1868

// Total young GC time
sun.gc.collector.0.time = 49520297

// Old GC collection count
sun.gc.collector.1.invocations = 68

// Total Old GC time
sun.gc.collector.1.time = 68420228

// Number of contended synchronizations
sun.rt._sync_ContendedLockAttempts = 23820

// Total stop-the-world pause time
sun.rt.safepointTime = 136996329

// Total time spent on entering safepoints
sun.rt.safepointSyncTime = 11110028
```

