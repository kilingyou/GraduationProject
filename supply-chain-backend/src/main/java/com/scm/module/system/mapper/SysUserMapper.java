package com.scm.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scm.module.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT u.*, r.role_key AS roleKey "
            + "FROM sys_user u "
            + "LEFT JOIN sys_user_role ur ON u.id = ur.user_id "
            + "LEFT JOIN sys_role r ON ur.role_id = r.id "
            + "WHERE u.username = #{username} AND u.del_flag = 0 AND u.status = 1 "
            + "LIMIT 1")
    SysUser selectByUsernameWithRole(@Param("username") String username);

    @Select("SELECT u.*, r.role_key AS roleKey "
            + "FROM sys_user u "
            + "LEFT JOIN sys_user_role ur ON u.id = ur.user_id "
            + "LEFT JOIN sys_role r ON ur.role_id = r.id "
            + "WHERE u.id = #{userId} AND u.del_flag = 0 "
            + "LIMIT 1")
    SysUser selectByIdWithRole(@Param("userId") Long userId);

    @Insert("INSERT INTO sys_user_role(user_id, role_id) VALUES(#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
