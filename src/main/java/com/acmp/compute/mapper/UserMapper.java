package com.acmp.compute.mapper;

import com.acmp.compute.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserMapper {

    int insert(User user);

    int update(User user);

    Optional<User> findById(@Param("id") String id);

    Optional<User> findByUsername(@Param("username") String username);

    List<String> findResourcePoolIdsByUserId(@Param("userId") String userId);

    int insertUserResourcePool(@Param("userId") String userId, @Param("resourcePoolId") String resourcePoolId);

    int deleteUserResourcePoolsByUserId(@Param("userId") String userId);
}
