package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public List<ShopType> queryRedis() {
        //先查询redis中有没有,key为cache:type:list
        String key = "cache:type:list";
        String typeJSON = stringRedisTemplate.opsForValue().get(key);
        //判断Redsi中是否有数据
        if(!StrUtil.isBlank(typeJSON)){
            List<ShopType> typeList = JSONUtil.toList(JSONUtil.parseArray(typeJSON), ShopType.class);
            return typeList;
        }
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if(typeList != null && !typeList.isEmpty()){
            String typeListJSON = JSONUtil.toJsonStr(typeList);
            stringRedisTemplate.opsForValue().set(key,typeListJSON);
        }
        return typeList;

    }
}
