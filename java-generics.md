# Java 泛型通配符
* ```<? extend T>``` 上界通配符
* ```<? spuer T>``` 下界通配符

网上或一些资料都是这么定义这两个通配符的意思，但没有说明“界”是在哪里。各种比喻，看着也比较混乱。 

# 界
当中说的“界”里面指的赋值的“界”，并且泛生出“读”(get())和“写”(add())的界

### ```<? extend T>```
```List<? extend T> list;```
list 可以接受任何T的子类进行赋值
```
X extend T
list = new ArrayList<X>();
```
赋值：list实际存放是数据是任意T的子类，“界”是T以下的类

“读”的时候由于list实际存放是T的任意子类都可以转换成T，所以都可以安全转换

“写”的时候就确定不了能不能存放了，例如A和B都是T的子类，赋值是```List<? extend T> list = new ArrayList<A>()```，显然List<A>是存放
不了B。鉴于这么不确定性编译器是处理不了，也会导致类型错误。（add(null) 是没问题的）

### ```<? spuer T>```
```
List<? spuer T> list;
```
理解了 extend 那么 super 就容易理解了

赋值：list 只能赋值 T 的超类，“界”是T以上类

读：A和B都是T的超类, ```List<? super T> ``存放的是T超类，那么读就会存在不确定是哪个超类(A or B)，只能读出所有的超类Object

写：由于超类的“下界”是T，那么list可以存放任何T的子类是没问题，因为子类转换成父类T

