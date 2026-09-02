package com.seckill.service;

import com.seckill.entity.User;

public interface UserService {
    // 注册：失败（用户名已存在）时抛出业务异常
    void register(String username, String password);

    // 登录：成功返回用户，失败抛出业务异常
    User login(String username, String password);
}