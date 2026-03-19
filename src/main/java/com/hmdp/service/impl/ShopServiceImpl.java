package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;



    @Resource
    private CacheClient cacheClient;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    @Override
    public Result queryById(Long id) throws InterruptedException {


        //Shop shop = cacheCLient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        Shop shop = cacheClient.queryWithLogincalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在!");
        }
        return Result.ok(shop);
    }

    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }


    private Shop queryWithMutex(Long id) throws InterruptedException {
        String key = CACHE_SHOP_KEY + id;
        Shop shop = null;
        //从redis查询商品缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        //如果命中直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        //判断命中的是否是空值
        if (shopJson != null) {
            return null;
        }


        String lockKey = "lock:shop:" + id;
        boolean isLock = tryLock(lockKey);
        try {
            if (isLock) {

                shop = getById(id);
                Thread.sleep(200);
                if (shop == null) {
                    stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return null;
                }
                String value = JSONUtil.toJsonStr(shop);
                stringRedisTemplate.opsForValue().set(key, value, LOCK_SHOP_TTL, TimeUnit.MINUTES);
            } else {
                Thread.sleep(50);
                queryWithMutex(id);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }

        return shop;


    }

    private void unlock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }

    private boolean tryLock(String lockKey) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey,"1",10L,TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);

    }


    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //1. 查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(300);

        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis
    stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    public Shop queryWithLogicExpire(Long id)  {
        String key = CACHE_SHOP_KEY +id;
        //从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(shopJson)){
            return null;
        }
        String ShopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY);
        RedisData redisData = JSONUtil.toBean(ShopJson,RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }else{
            //尝试获取锁
            String lockKey = LOCK_SHOP_KEY+id;
            Boolean isLock = tryLock(lockKey);
            if(isLock){
                //开启新线程
                CACHE_REBUILD_EXECUTOR.submit(
                        ()->{
                            try{
                                this.saveShop2Redis(id,30L);
                            }catch(Exception e){
                                throw new RuntimeException(e);
                            }finally {
                                unlock(lockKey);
                            }

                        }
                );
            }
            return shop;
        }
    }



}

