package com.scm.security;

import com.scm.module.system.entity.SysUser;
import com.scm.module.system.mapper.SysUserMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    public UserDetailsServiceImpl(@Lazy SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectByUsernameWithRole(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        String roleKey = user.getRoleKey();
        String authority = StringUtils.hasText(roleKey)
                ? (roleKey.startsWith("ROLE_") ? roleKey : "ROLE_" + roleKey)
                : "ROLE_USER";
        return new LoginUser(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                roleKey,
                user.getBlockchainAddr(),
                Collections.singletonList(new SimpleGrantedAuthority(authority)),
                user.getStatus() != null && user.getStatus() == 1);
    }
}
