package com.seckill.service.impl;

import com.seckill.entity.User;
import com.seckill.mapper.UserMapper;
import com.seckill.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userMapper);
    }

    @Test
    void registerStoresBcryptHash() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        service.register("alice", "secret123");

        verify(userMapper).insert(any(User.class));
    }

    @Test
    void loginAcceptsCorrectPassword() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword(PasswordUtil.encrypt("secret123"));
        when(userMapper.selectOne(any())).thenReturn(user);

        User result = service.login("alice", "secret123");

        assertEquals(1L, result.getId());
    }
}
