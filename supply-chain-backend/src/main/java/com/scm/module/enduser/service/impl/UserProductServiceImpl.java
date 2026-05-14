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

    /**
     * 将终端用户与已售商品 SN 绑定：实名销售校验购买者身份哈希，匿名销售仅校验 SN 已销售，
     * 通过后落库并上链锚定绑定事实。
     */
    @Override
    public UserProduct bindProduct(Long userId, String sn, String customerName, String customerPhone) {
        // SN 必填，后续统一用 trim 后的值查询与存储
        if (sn == null || sn.trim().isEmpty()) {
            throw new BusinessException("SN 不能为空");
        }
        String normalizedSn = sn.trim();
        // 同一用户重复绑定同一 SN 时直接返回已有记录，保证幂等
        UserProduct exist = getOne(new LambdaQueryWrapper<UserProduct>()
                .eq(UserProduct::getUserId, userId)
                .eq(UserProduct::getSn, normalizedSn)
                .last("LIMIT 1"));
        if (exist != null) {
            return exist;
        }
        // 必须先有分销商侧销售登记，否则无法核对购买者信息
        SalesRecord sale = salesRecordService.getLatestBySn(normalizedSn);
        if (sale == null) {
            throw new BusinessException("该 SN 尚无销售登记，无法绑定");
        }
        String cn = customerName != null ? customerName.trim() : "";
        String cp = customerPhone != null ? customerPhone.trim() : "";
        String verificationHash;
        if (Integer.valueOf(1).equals(sale.getCustomerAnonymous())) {
            if (sale.getCustomerHash() == null || sale.getCustomerHash().trim().isEmpty()) {
                throw new BusinessException("销售登记信息异常，无法验证匿名购买记录");
            }
            verificationHash = sale.getCustomerHash();
        } else {
            // 与销售登记时相同的拼接规则与 SHA-256，允许姓名或手机号为空。
            String computed = HashUtil.sha256Hex((cn + "|" + cp).getBytes(StandardCharsets.UTF_8));
            if (sale.getCustomerHash() == null || !sale.getCustomerHash().equalsIgnoreCase(computed)) {
                throw new BusinessException("姓名/手机号与销售登记信息不一致，无法验证为合法购买者");
            }
            verificationHash = computed;
        }

        UserProduct bind = new UserProduct();
        bind.setUserId(userId);
        bind.setSn(normalizedSn);
        bind.setVerifyStatus("VERIFIED");
        // 上链内容为业务载荷的哈希，类型标识 USER_PRODUCT_BIND，便于链上审计
        String payload = userId + "|" + normalizedSn + "|" + verificationHash;
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

