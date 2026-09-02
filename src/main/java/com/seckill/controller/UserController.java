package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.entity.User;
import com.seckill.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/register")
    public Result<Void> register(@RequestParam String username,
                                 @RequestParam String password) {
        userService.register(username, password);
        return Result.success();
    }

    @GetMapping("/login")
    public Result<User> login(@RequestParam String username,
                              @RequestParam String password) {
        User user = userService.login(username, password);
        return Result.success(user);
    }
}