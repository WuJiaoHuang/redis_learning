package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.jni.Local;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Component
@Slf4j
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(value),time,timeUnit);
    }


    public void setWithLogicalExpire(String key,Object value,Long time,TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }


    /*
    * <R,ID>声明两个泛型类型参数
    * R:返回类型(Result)
    * ID:id的类型(可以是Long,String,Integer等)
    * Class<R> type:用于反序列化时知道要转换为什么类型
    * */
    public <R,ID> R queryWithPassThrough(String keyPrefix , ID id, Class<R> type, Function<ID,R> dbFallback,Long time,TimeUnit unit){
        String key = keyPrefix+id;
        //先从redis中查找有没有缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json,type);
        }
        if(json != null){
            return null;
        }
        R r = dbFallback.apply(id);
        if(r == null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        this.set(key,r,time,unit);
        return r;

    }


    public <R,ID> R queryWithLogincalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> function,Long time,TimeUnit unit){
        String key = keyPrefix+id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(json)){
            return null;
        }
        //命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json,RedisData.class);
        R r = JSONUtil.toBean((JSONObject)redisData.getData(),type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            return r;
        }

        String lockKey = LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit(()-> {
                        try {
                            R r1 = function.apply(id);
                            this.setWithLogicalExpire(key, r1, time, unit);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        finally {
                            unlock(lockKey);
                        }
                    }

                    );

        }
        return r;
    }


    /**
     * 自动拆箱装箱情况:
     * 1.编译器看到的是表达式，可以进行类型推断和转换
     *     private boolean method1() {
     *         // 表达式类型是 Boolean，方法返回类型是 boolean
     *         // 编译器可以自动插入拆箱代码
     *         return getBoolean();  // ✅ OK
     *     }
     * 不能自动拆箱情况
     * 想赋值给变量
     *     private boolean wrong(String key) {
     *         Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1");
     *         // 这里 flag 已经被声明为 Boolean 类型
     *         // 返回时，编译器认为你要返回这个 Boolean 变量
     *         // 但方法返回类型是 boolean，类型不匹配
     *         return flag;  // ❌ 编译错误
     *     }
     */
    /**
     * 用BooleanUtil.isTrue()的原因:
     * stringRedisTemplate.opsForValue().setIfAbsent() 返回的是Boolean对象，不是boolean基本类型，所以flag可能是null
     *错误写法
     * private boolean tryLock(String key){
     *     Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1");
     *     return flag;  // 编译错误！不能将 Boolean 直接赋值给 boolean
     * }
     * 1.先把Boolean值赋给flag再返回，但要求的是boolean,不能自动拆箱
     * 2.返回结果可能为null(Redis连接超时或异常可能会返回null)
     * BooleanUtil.isTrue(flag)相当于手写
     * if(flag == null){return false;}
     * return flag.booleanValue();//安全拆箱
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1");
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }


}
