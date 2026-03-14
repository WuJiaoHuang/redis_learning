package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

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

    @Override
    public Result queryById(Long id) throws InterruptedException {

        Shop shop = queryWithMutex(id);
        if(shop == null){
            return Result.fail("店铺不存在!");
        }
        return Result.ok(shop);
    }





    public Shop queryWithMutex(Long id) throws InterruptedException {
        String key = CACHE_SHOP_KEY+id;
        Shop shop = null;
        //从redis查询商品缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        //如果命中直接返回
        if(StrUtil.isNotBlank(shopJson)){
            shop  = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        //判断命中的是否是空值
        if(shopJson != null){
            return null;
        }


        String lockKey = "lock:shop:"+id;
        boolean isLock = tryLock(lockKey);
        try{
            if(isLock){

                shop = getById(id);
                Thread.sleep(200);
                if(shop == null){
                    stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                    return null;
                }
                String value = JSONUtil.toJsonStr(shop);
                stringRedisTemplate.opsForValue().set(key,value,LOCK_SHOP_TTL,TimeUnit.MINUTES);
            }else{
                Thread.sleep(50);
                queryWithMutex(id);
            }
        }catch(InterruptedException e){
            throw new RuntimeException(e);
        }finally{
            unlock(lockKey);
        }

        return shop;


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


    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10L,TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
