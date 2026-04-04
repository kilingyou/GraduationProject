package com.scm.module.manufacturer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.Constants;
import com.scm.common.PageResult;
import com.scm.common.exception.BusinessException;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.module.manufacturer.dto.RejectRecordVO;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.entity.RejectRecord;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.manufacturer.service.RejectDispositionService;
import com.scm.module.manufacturer.service.RejectRecordService;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.service.ProductionRequestService;
import com.scm.module.system.entity.SysUser;
import com.scm.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RejectDispositionServiceImpl implements RejectDispositionService {

    private final RejectRecordService rejectRecordService;
    private final ProductionRequestService productionRequestService;
    private final DeviceRecordService deviceRecordService;
    private final BlockchainAnchorService blockchainAnchorService;
    private final SysUserMapper sysUserMapper;

    @Override
    public PageResult<RejectRecordVO> pageForSupplier(Long supplierId, int pageNum, int pageSize) {
        Page<RejectRecord> p = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RejectRecord> w = new LambdaQueryWrapper<RejectRecord>()
                .apply("order_id IN (SELECT order_id FROM bus_production_request WHERE supplier_id = {0})",
                        supplierId)
                .orderByDesc(RejectRecord::getCreateTime);
        IPage<RejectRecord> raw = rejectRecordService.page(p, w);
        return toPageResult(raw, true);
    }

    @Override
    public PageResult<RejectRecordVO> pageForManufacturer(Long manufacturerId, int pageNum, int pageSize) {
        Page<RejectRecord> p = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RejectRecord> w = new LambdaQueryWrapper<RejectRecord>()
                .eq(RejectRecord::getManufacturerId, manufacturerId)
                .orderByDesc(RejectRecord::getCreateTime);
        IPage<RejectRecord> raw = rejectRecordService.page(p, w);
        return toPageResult(raw, false);
    }

    private PageResult<RejectRecordVO> toPageResult(IPage<RejectRecord> raw, boolean loadManufacturerName) {
        List<RejectRecordVO> vos = raw.getRecords().stream()
                .map(r -> toVo(r, loadManufacturerName))
                .collect(Collectors.toList());
        return new PageResult<RejectRecordVO>()
                .setRecords(vos)
                .setTotal(raw.getTotal())
                .setCurrent(raw.getCurrent())
                .setSize(raw.getSize());
    }

    private RejectRecordVO toVo(RejectRecord r, boolean loadManufacturerName) {
        RejectRecordVO v = new RejectRecordVO();
        BeanUtils.copyProperties(r, v);
        if (loadManufacturerName && r.getManufacturerId() != null) {
            SysUser u = sysUserMapper.selectById(r.getManufacturerId());
            if (u != null) {
                v.setManufacturerName(StringUtils.hasText(u.getEnterpriseName())
                        ? u.getEnterpriseName() : u.getUsername());
            }
        }
        return v;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReturnBySupplier(Long recordId, Long supplierId) {
        RejectRecord r = rejectRecordService.getById(recordId);
        if (r == null) {
            throw new BusinessException("记录不存在");
        }
        if (!Constants.DISPOSAL_RETURN.equals(r.getDisposalType())) {
            throw new BusinessException("该记录不是退货处置");
        }
        if (!Constants.DISPOSAL_AWAITING_SUPPLIER.equals(r.getDisposalStatus())) {
            throw new BusinessException("当前状态不可确认退货");
        }
        if (!StringUtils.hasText(r.getOrderId())) {
            throw new BusinessException("记录缺少关联订单");
        }
        ProductionRequest order = productionRequestService.getOne(
                new LambdaQueryWrapper<ProductionRequest>()
                        .eq(ProductionRequest::getOrderId, r.getOrderId()));
        if (order == null || !supplierId.equals(order.getSupplierId())) {
            throw new BusinessException("无权确认该退货");
        }
        String payload = "RETURN_DONE|" + r.getId() + "|" + r.getEcid() + "|" + r.getOrderId();
        String tx = blockchainAnchorService.anchor("MFG_REJECT_RETURN_DONE", HashUtil.sha256Hex(payload));
        r.setDisposalStatus(Constants.DISPOSAL_COMPLETED);
        r.setDisposalCompleteTxHash(tx);
        rejectRecordService.updateById(r);
        markDeviceChainAfterDisposal(r.getEcid(), r.getManufacturerId(), tx);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmDestroyByManufacturer(Long recordId, Long manufacturerId) {
        RejectRecord r = rejectRecordService.getById(recordId);
        if (r == null) {
            throw new BusinessException("记录不存在");
        }
        if (!manufacturerId.equals(r.getManufacturerId())) {
            throw new BusinessException("无权操作该记录");
        }
        if (!Constants.DISPOSAL_DESTROY.equals(r.getDisposalType())) {
            throw new BusinessException("该记录不是销毁处置");
        }
        if (!Constants.DISPOSAL_AWAITING_MFG_DESTROY.equals(r.getDisposalStatus())) {
            throw new BusinessException("当前状态不可确认销毁");
        }
        String payload = "DESTROY_DONE|" + r.getId() + "|" + r.getEcid() + "|" + r.getOrderId();
        String tx = blockchainAnchorService.anchor("MFG_REJECT_DESTROY_DONE", HashUtil.sha256Hex(payload));
        r.setDisposalStatus(Constants.DISPOSAL_COMPLETED);
        r.setDisposalCompleteTxHash(tx);
        rejectRecordService.updateById(r);
        markDeviceChainAfterDisposal(r.getEcid(), r.getManufacturerId(), tx);
    }

    /**
     * 退货/销毁处置完结上链后，ECID 列表「是否上链」展示为已上链；无历史 txHash 时写入本次处置锚定哈希。
     */
    private void markDeviceChainAfterDisposal(String ecid, Long manufacturerId, String disposalTxHash) {
        if (!StringUtils.hasText(ecid) || manufacturerId == null) {
            return;
        }
        DeviceRecord d = deviceRecordService.getOne(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getEcid, ecid)
                .eq(DeviceRecord::getManufacturerId, manufacturerId));
        if (d == null) {
            return;
        }
        d.setChainRegistered(1);
        if (!StringUtils.hasText(d.getTxHash())) {
            d.setTxHash(disposalTxHash);
        }
        deviceRecordService.updateById(d);
    }
}
