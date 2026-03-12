package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;



    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号.如果不符合，返回错误消息
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到Redis

        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //发送验证码
        log.info("发送验证码成功，验证码为{}",code);


        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //检验手机号
         String phone = loginForm.getPhone();
         if(RegexUtils.isPhoneInvalid(phone)){
             return Result.fail("手机格式错误!");
         }

        //TODO 从redis获取验证码并检验
        String code = (String)stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        if(code == null||!code.equals(loginForm.getCode())){
            return Result.fail("验证码错误");
        }
        //一致，根据手机号查询用户
        User user = query().eq("phone",phone).one();
        //判断用户是否存在
        if(user == null){
            user = User.builder().phone(phone).nickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10)).build();
            save(user);
        }

        UserDTO userDTO = new UserDTO();
        //不存在，创建新用户并保存
        BeanUtils.copyProperties(user,userDTO);
        //TODO 保存用户信息到redis中
        //1 随机生成token,作为登录令牌
        String token = UUID.randomUUID().toString();
        //将User对象转为Hash存储
        UserDTO u = BeanUtil.copyProperties(user,UserDTO.class);
        Map<String,Object> userMap = BeanUtil.beanToMap(u,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue)-> fieldValue == null ? null :fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);

        //设置token有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);



        //存储


        //返回token
        session.setAttribute("user", userDTO);
        return Result.ok();
    }
}
