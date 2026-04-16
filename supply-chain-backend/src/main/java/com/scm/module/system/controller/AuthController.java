package com.scm.module.system.controller;

import com.scm.common.Result;
import com.scm.module.system.entity.SysMenu;
import com.scm.module.system.entity.SysUser;
import com.scm.module.system.service.SysMenuService;
import com.scm.module.system.service.SysUserService;
import com.scm.security.JwtTokenProvider;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserService sysUserService;
    private final SysMenuService sysMenuService;

    //不同账号登录时走这个方法，校验账号密码后，签发JWT令牌，并配置前端路由所需信息
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        String token = jwtTokenProvider.generateToken(loginUser.getUserId(), loginUser.getUsername(), loginUser.getRoleKey());

        SysUser user = sysUserService.getUserInfo(loginUser.getUserId());
        List<SysMenu> menus = sysMenuService.getMenuTree(loginUser.getUserId());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", user);
        data.put("menus", menus);
        return Result.ok(data);
    }


    //非供应商注册模块
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
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

    //带资质证书等多元注册，供应商注册时走这里
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Void> registerMultipart(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) String enterpriseName,
            @RequestParam(required = false) String creditCode,
            @RequestParam(required = false) String contactPerson,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam String roleKey,
            @RequestParam(value = "files", required = false) MultipartFile[] files) {
        //创建系统用户实体，并设置相关信息
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(password);
        user.setEnterpriseName(enterpriseName);
        user.setCreditCode(creditCode);
        user.setContactPerson(contactPerson);
        user.setPhone(phone);
        user.setEmail(email);
        //将资质证书与营业执照存进qualification列表
        List<MultipartFile> qualification = files == null ? Collections.<MultipartFile>emptyList()
                : Arrays.stream(files).filter(f -> f != null && !f.isEmpty()).collect(Collectors.toList());
        if ("supplier".equalsIgnoreCase(roleKey)) {
            //如果是供应商就走供应商注册
            sysUserService.register(user, roleKey, qualification);
        } else {
            sysUserService.register(user, roleKey);
        }
        return Result.ok();
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SysUser userInfo = sysUserService.getUserInfo(loginUser.getUserId());
        List<SysMenu> menus = sysMenuService.getMenuTree(loginUser.getUserId());
        Map<String, Object> data = new HashMap<>();
        data.put("user", userInfo);
        data.put("menus", menus);
        return Result.ok(data);
    }
}
