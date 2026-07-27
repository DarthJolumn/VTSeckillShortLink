package com.jolumn.vtsluser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jolumn.vtsluser.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
