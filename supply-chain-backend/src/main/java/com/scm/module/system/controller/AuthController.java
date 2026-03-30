package com.scm.module.system.controller;

import com.scm.common.Result;
import com.scm.module.system.entity.SysUser;
import com.scm.module.system.service.SysUserService;
import com.scm.security.JwtTokenProvider;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserService sysUserService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        String token = jwtTokenProvider.generateToken(loginUser.getUserId(), loginUser.getUsername(), loginUser.getRoleKey());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", loginUser.getUserId());
        data.put("username", loginUser.getUsername());
        data.put("roleKey", loginUser.getRoleKey());
        data.put("blockchainAddr", loginUser.getBlockchainAddr());
        return Result.ok(data);
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody Map<String, Object> params) {
        SysUser user = new SysUser();
        user.setUsername((String) params.get("username"));
        user.setPassword((String) params.get("password"));
        user.setEnterpriseName((String) params.get("enterpriseName"));
        user.setCreditCode((String) params.get("creditCode"));
        user.setContactPerson((String) params.get("contactPerson"));
        user.setPhone((String) params.get("phone"));
        user.setEmail((String) params.get("email"));

        String roleKey = (String) params.get("roleKey");
        sysUserService.register(user, roleKey);
        return Result.ok();
    }

    @GetMapping("/info")
    public Result<SysUser> info() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SysUser userInfo = sysUserService.getUserInfo(loginUser.getUserId());
        return Result.ok(userInfo);
    }
}
