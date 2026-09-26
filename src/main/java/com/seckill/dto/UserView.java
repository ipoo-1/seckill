package com.seckill.dto;

import com.seckill.entity.User;

import java.time.LocalDateTime;

public record UserView(Long id, String username, LocalDateTime createTime) {

    public static UserView from(User user) {
        return new UserView(user.getId(), user.getUsername(), user.getCreateTime());
    }
}
