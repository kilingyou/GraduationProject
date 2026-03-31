package com.scm.module.enduser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.exception.BusinessException;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.module.distributor.entity.SalesRecord;
import com.scm.module.distributor.service.SalesRecordService;
import com.scm.module.enduser.entity.UserProduct;
import com.scm.module.enduser.mapper.UserProductMapper;
import com.scm.module.enduser.service.UserProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserProductServiceImpl extends ServiceImpl<UserProductMapper, UserProduct>
        implements UserProductService {

    private final SalesRecordService salesRecordService;
    private final BlockchainAnchorService blockchainAnchorService;

    @Override
    public UserProduct bindProduct(Long userId, String sn, String customerName, String customerPhone) {
        if (sn == null || sn.trim().isEmpty()) {
            throw new BusinessException("SN 不能为空");
        }
        String normalizedSn = sn.trim();
        UserProduct exist = getOne(new LambdaQueryWrapper<UserProduct>()
                .eq(UserProduct::getUserId, userId)
                .eq(UserProduct::getSn, normalizedSn)
                .last("LIMIT 1"));
        if (exist != null) {
            return exist;
        }
        SalesRecord sale = salesRecordService.getLatestBySn(normalizedSn);
        if (sale == null) {
            throw new BusinessException("该 SN 尚无销售登记，无法绑定");
        }
        String cn = customerName != null ? customerName.trim() : "";
        String cp = customerPhone != null ? customerPhone.trim() : "";
        if (cp.isEmpty()) {
            throw new BusinessException("请填写购买时登记的手机号，用于与销售凭证哈希比对");
        }
        String computed = HashUtil.sha256Hex((cn + "|" + cp).getBytes(StandardCharsets.UTF_8));
        if (sale.getCustomerHash() == null || !sale.getCustomerHash().equalsIgnoreCase(computed)) {
            throw new BusinessException("姓名/手机号与销售登记信息不一致，无法验证为合法购买者");
        }

        UserProduct bind = new UserProduct();
        bind.setUserId(userId);
        bind.setSn(normalizedSn);
        bind.setVerifyStatus("VERIFIED");
        String payload = userId + "|" + normalizedSn + "|" + computed;
        bind.setTxHash(blockchainAnchorService.anchor("USER_PRODUCT_BIND", HashUtil.sha256Hex(payload)));
        save(bind);
        return bind;
    }

    @Override
    public boolean isBound(Long userId, String sn) {
        if (sn == null || sn.trim().isEmpty()) {
            return false;
        }
        return count(new LambdaQueryWrapper<UserProduct>()
                .eq(UserProduct::getUserId, userId)
                .eq(UserProduct::getSn, sn.trim())) > 0;
    }

    @Override
    public List<UserProduct> listByUser(Long userId) {
        return list(new LambdaQueryWrapper<UserProduct>()
                .eq(UserProduct::getUserId, userId)
                .orderByDesc(UserProduct::getBindTime));
    }
}

