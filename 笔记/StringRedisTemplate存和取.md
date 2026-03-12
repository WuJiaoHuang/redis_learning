


### 1. 存和取字符串
```java

stringRedisTemplate.opsForValue().set("name","张三");

String name = stringRedisTemplate().opsForValue().get("name");


```

### 2.存对象:转换为JSON字符串

存的时候:Java对象->JSON字符串

ShopType shop