# Sptring @Import 作用 TODO

## 作用
通过导入的方式（有三种）把实例加入到 SpringIOC 容器

## 三种方式
文档描述
```
Indicates one or more @Configuration classes to import.
Provides functionality equivalent to the <import/> element in Spring XML. Allows for importing @Configuration classes, ImportSelector and ImportBeanDefinitionRegistrar implementations, as well as regular component classes (as of 4.2; analogous to AnnotationConfigApplicationContext.register(java.lang.Class<?>...)).
@Bean definitions declared in imported @Configuration classes should be accessed by using @Autowired injection. Either the bean itself can be autowired, or the configuration class instance declaring the bean can be autowired. The latter approach allows for explicit, IDE-friendly navigation between @Configuration class methods.
May be declared at the class level or as a meta-annotation.
If XML or other non-@Configuration bean definition resources need to be imported, use the @ImportResource annotation instead.
```

### 直接填对应的```Class```


### 填```ImportSelector```实现类


### 填```ImportBeanDefinitionRegistrar```实现类
