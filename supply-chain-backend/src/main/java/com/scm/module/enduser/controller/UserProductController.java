package com.scm.module.enduser.controller;

import com.scm.common.Result;
import com.scm.module.enduser.entity.UserProduct;
import com.scm.module.enduser.service.UserProductService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/enduser/product")
@RequiredArgsConstructor
public class UserProductController {

    private final UserProductService userProductService;

    @PostMapping("/bind")
    public Result<UserProduct> bind(@RequestBody Map<String, String> body) {
        LoginUser user = currentUser();
        String sn = body.get("sn");
        String customerName = body.get("customerName");
        String customerPhone = body.get("customerPhone");
        UserProduct bound = userProductService.bindProduct(user.getUserId(), sn, customerName, customerPhone);
        return Result.ok(bound);
    }

    @GetMapping("/list")
    public Result<List<UserProduct>> list() {
        LoginUser user = currentUser();
        return Result.ok(userProductService.listByUser(user.getUserId()));
    }

    private LoginUser currentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}

