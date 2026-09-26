package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.dto.LoginRequest;
import com.seckill.dto.RegisterRequest;
import com.seckill.dto.UserView;
import com.seckill.entity.User;
import com.seckill.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request.username(), request.password());
        return Result.success();
    }

    @PostMapping("/login")
    public Result<UserView> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.login(request.username(), request.password());
        return Result.success(UserView.from(user));
    }
}
