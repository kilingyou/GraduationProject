package com.scm.module.enduser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.enduser.entity.UserProduct;

import java.util.List;

public interface UserProductService extends IService<UserProduct> {

    /**
     * 绑定产品：需存在销售记录，且姓名+手机号与销售环节生成的 customerHash 一致（PDF 购买者校验）。
     */
    UserProduct bindProduct(Long userId, String sn, String customerName, String customerPhone);

    boolean isBound(Long userId, String sn);

    List<UserProduct> listByUser(Long userId);
}

