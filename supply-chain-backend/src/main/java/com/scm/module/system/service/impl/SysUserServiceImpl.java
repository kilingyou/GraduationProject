package com.scm.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.exception.BusinessException;
import com.scm.module.system.entity.SysRole;
import com.scm.module.system.entity.SysUser;
import com.scm.module.system.mapper.SysRoleMapper;
import com.scm.module.system.mapper.SysUserMapper;
import com.scm.module.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(SysUser user, String roleKey) {
        Long existCount = baseMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername()));
        if (existCount > 0) {
            throw new BusinessException("用户名已存在");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        user.setDelFlag(0);
        user.setBlockchainAddr("0x" + UUID.randomUUID().toString().replace("-", "").substring(0, 40));
        baseMapper.insert(user);

        if (StringUtils.hasText(roleKey)) {
            SysRole role = sysRoleMapper.selectOne(
                    new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, roleKey));
            if (role != null) {
                baseMapper.insertUserRole(user.getId(), role.getId());
            }
        }
    }

    @Override
    public SysUser getUserInfo(Long userId) {
        SysUser user = baseMapper.selectByIdWithRole(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    @Override
    public void updateProfile(SysUser user) {
        user.setPassword(null);
        user.setUsername(null);
        user.setDelFlag(null);
        user.setStatus(null);
        baseMapper.updateById(user);
    }

    @Override
    public IPage<SysUser> listUsers(Page<SysUser> page, String username, String roleKey) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(username), SysUser::getUsername, username);
        wrapper.eq(SysUser::getDelFlag, 0);
        wrapper.orderByDesc(SysUser::getCreateTime);
        IPage<SysUser> result = baseMapper.selectPage(page, wrapper);
        result.getRecords().forEach(u -> u.setPassword(null));
        return result;
    }
}
