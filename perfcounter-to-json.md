### 输出计数器
jcmd \<pid\> PerfCounter.print 等于 jstat -snap \<pid\>

### 用python转换成json格式,每秒收集一次
```python
#!/usr/bin/python3
import os,sys,json,time

# 转换成json并保存
#!/usr/bin/python3
import os,sys,json,time

#转换数据类型
def convertDataType(val):
  itemIndex = val.find("\"")
  if itemIndex == -1 :
    try :
      val  = int(val)
    except ValueError:
      try:
        val = float(val)
      except ValueError:
        val = val
  else :
    val = val.replace("\"","")
  return val

#转换字段层级
def keyHandler(key):
  appendTotal = ["sun.cls.appClassLoadTime","sun.cls.defineAppClassTime","sun.cls.classVerifyTime","sun.cls.classLinkedTime","sun.cls.parseClassTime","sun.cls.classInitTime"]
  if appendTotal.count(key) > 0 :
    key += ".total"
  return key
# 转换成json并保存
def appendToFile(newStr,outPutFile):
  index = newStr.index("\n");
  newStr = newStr[index+1:-1]
  paramList=newStr.splitlines()
  jsonObj={}
  for item in paramList :
      itemList = item.split("=")
      val = itemList[1]
      val = convertDataType(val)
      key = itemList[0]
      key = keyHandler(key)
      jsonObj[key] = val
  jsonStr = json.dumps(jsonObj)
  f = open(outPutFile, "a")
  f.write(jsonStr+"\n")
  f.close()

def readSourceData(appId):
  cmd = "jcmd " + appId + " PerfCounter.print"
  pcOut = os.popen(cmd)
  pcStr = pcOut.read()
  return pcStr
  appendToFile(pcStr,appendFile)

def main():
  appendFile = "/opt/data/perfcounter.json"
  appPidPath = "/opt/data/app.pid"

  f = open(appPidPath, "r")
  appId = f.read()
  appId = appId.replace('\n','')

  while True:
    pcStr = readSourceData(appId)
    appendToFile(pcStr,appendFile)
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

