package com.kama.service;


import com.kama.annotation.Retryable;
import com.kama.pojo.User;



public interface UserService {

    // 查询
    @Retryable
    User getUserByUserId(Integer id);

    // 新增
    @Retryable
    Integer insertUserId(User user);
}
