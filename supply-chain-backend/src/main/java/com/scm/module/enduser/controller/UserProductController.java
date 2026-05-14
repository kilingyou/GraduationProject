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

    /**
     * 终端用户将已售产品绑定到本人账号。
     * 服务层会校验该 SN 已存在销售登记；实名销售继续比对姓名、手机号哈希，匿名销售允许仅凭 SN 绑定。
     *
     * @param body JSON，字段：{@code sn} 整机序列号；{@code customerName}、{@code customerPhone} 可选
     * @return 绑定成功后的 {@link UserProduct} 记录
     */
    @PostMapping("/bind")
    public Result<UserProduct> bind(@RequestBody Map<String, String> body) {
        // 绑定主体为当前登录的终端用户
        LoginUser user = currentUser();
        // 从请求体读取 SN 与购买者身份信息（用于与销售凭证哈希比对）
        String sn = body.get("sn");
        String customerName = body.get("customerName");
        String customerPhone = body.get("customerPhone");
        // 校验通过后落库用户与产品的关联关系
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

