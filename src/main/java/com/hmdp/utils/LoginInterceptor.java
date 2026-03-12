package com.hmdp.utils;


import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.websocket.Session;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@NoArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {


    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, java.lang.Object handler) throws java.lang.Exception {
        //判断是否需要拦截(ThreadLocal中是否有用户)
        if(UserHolder.getUser() == null){
            response.setStatus(401);
            //拦截
            return false;
        }
        //有用户，则放行
        return true;
    }




}
