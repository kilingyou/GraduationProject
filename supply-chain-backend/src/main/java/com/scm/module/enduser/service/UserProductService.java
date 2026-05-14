package com.scm.module.enduser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.enduser.entity.UserProduct;

import java.util.List;

public interface UserProductService extends IService<UserProduct> {

    /**
     * 绑定产品：需存在销售记录；实名销售校验姓名+手机号哈希，匿名销售允许仅凭 SN 绑定。
     */
    UserProduct bindProduct(Long userId, String sn, String customerName, String customerPhone);

    boolean isBound(Long userId, String sn);

    List<UserProduct> listByUser(Long userId);
}

