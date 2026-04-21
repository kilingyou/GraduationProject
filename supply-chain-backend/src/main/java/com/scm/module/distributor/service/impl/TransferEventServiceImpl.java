package com.scm.module.distributor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.exception.BusinessException;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.integration.blockchain.SmartContractInvokeService;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.module.distributor.entity.TransferEvent;
import com.scm.module.distributor.mapper.TransferEventMapper;
import com.scm.module.distributor.service.TransferEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferEventServiceImpl
        extends ServiceImpl<TransferEventMapper, TransferEvent>
        implements TransferEventService {

    private final AssemblyRecordService assemblyRecordService;
    private final BlockchainAnchorService blockchainAnchorService;
    private final SmartContractInvokeService smartContractInvokeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferEvent shipTransfer(String sn, Long senderId, Long receiverId, String logisticsCompany,
                                      String trackingNumber, LocalDateTime shipTime, LocalDateTime estimatedArrival,
                                      String transferType) {
        if (!StringUtils.hasText(sn)) {
            throw new BusinessException("SN 不能为空");
        }
        if (receiverId == null || senderId.equals(receiverId)) {
            throw new BusinessException("接收方无效");
        }
        if (!StringUtils.hasText(trackingNumber)) {
            throw new BusinessException("请填写物流单号");
        }
        if (!StringUtils.hasText(logisticsCompany)) {
            throw new BusinessException("请填写物流公司");
        }

        String snNorm = sn.trim();
        AssemblyRecord ar = assemblyRecordService.listBySn(snNorm);
        if (ar == null) {
            throw new BusinessException("未找到该 SN 的组装记录，无法发货");
        }
        if (ar.getCurrentHolderId() == null || !ar.getCurrentHolderId().equals(senderId)) {
            throw new BusinessException("无权发货：您不是该产品的当前货权方（需先完成上链与收货流程）");
        }
        if ("SOLD".equals(ar.getStatus()) || "DECOMMISSIONED".equals(ar.getStatus())) {
            throw new BusinessException("该产品状态不允许再流转");
        }

        long open = count(new LambdaQueryWrapper<TransferEvent>()
                .eq(TransferEvent::getSn, snNorm)
                .in(TransferEvent::getStatus, "PENDING", "IN_TRANSIT"));
        if (open > 0) {
            throw new BusinessException("该 SN 已有在途物流单，请先由收货方确认收货");
        }

        LocalDateTime st = shipTime != null ? shipTime : LocalDateTime.now();
        TransferEvent te = new TransferEvent()
                .setSn(snNorm)
                .setBatchNo(ar.getAssemblyBatchNo())
                .setLogisticsCompany(logisticsCompany.trim())
                .setTrackingNumber(trackingNumber.trim())
                .setSenderId(senderId)
                .setReceiverId(receiverId)
                .setTransferType(StringUtils.hasText(transferType) ? transferType.trim() : "SHIP")
                .setShipTime(st)
                .setEstimatedArrival(estimatedArrival)
                .setStatus("IN_TRANSIT");

        String payload = snNorm + "|" + te.getTrackingNumber() + "|" + senderId + "|" + receiverId + "|" + st;
        te.setTxHash(blockchainAnchorService.anchor("TRANSFER_EVENT", HashUtil.sha256Hex(payload)));
        smartContractInvokeService.logTransfer(
                snNorm,
                te.getTrackingNumber(),
                receiverId,
                te.getTransferType()
        );

        save(te);

        ar.setStatus("IN_TRANSIT");
        assemblyRecordService.updateById(ar);
        return te;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<TransferEvent> shipBatch(List<String> sns, Long senderId, Long receiverId, String logisticsCompany,
                                         String trackingNumber, LocalDateTime shipTime, LocalDateTime estimatedArrival,
                                         String transferType) {
        if (sns == null || sns.isEmpty()) {
            throw new BusinessException("请提供至少一个 SN");
        }
        List<String> distinct = sns.stream().filter(StringUtils::hasText).map(String::trim).distinct()
                .collect(Collectors.toList());
        List<TransferEvent> out = new ArrayList<>(distinct.size());
        for (String sn : distinct) {
            out.add(shipTransfer(sn, senderId, receiverId, logisticsCompany, trackingNumber, shipTime,
                    estimatedArrival, transferType));
        }
        return out;
    }

    /**
     * 收货确认：按 transferId、物流单号或 SN 定位当前收货方待签收记录并批量签收。
     *
     * @param receiverId 收货方用户 ID
     * @param transferId 流转记录 ID（可选）
     * @param trackingNumber 物流单号（可选）
     * @param sn 产品 SN（可选）
     * @return 首条被签收的流转记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferEvent receiveTransfer(Long receiverId, Long transferId, String trackingNumber, String sn) {
        // 构建待收货查询：仅查询当前收货方名下、状态为待处理/在途的物流记录
        LambdaQueryWrapper<TransferEvent> w = new LambdaQueryWrapper<TransferEvent>()
                .eq(TransferEvent::getReceiverId, receiverId)
                .in(TransferEvent::getStatus, "PENDING", "IN_TRANSIT");
        // 可选条件：按物流流转记录 ID 精确定位
        if (transferId != null) {
            w.eq(TransferEvent::getId, transferId);
        }
        // 可选条件：按物流单号过滤（去除首尾空格）
        if (StringUtils.hasText(trackingNumber)) {
            w.eq(TransferEvent::getTrackingNumber, trackingNumber.trim());
        }
        // 可选条件：按 SN 过滤（去除首尾空格）
        if (StringUtils.hasText(sn)) {
            w.eq(TransferEvent::getSn, sn.trim());
        }
        // 按主键升序，确保处理顺序稳定
        w.orderByAsc(TransferEvent::getId);
        List<TransferEvent> pending = list(w);
        if (pending.isEmpty()) {
            throw new BusinessException("未找到待收货记录，请核对运单号或 SN");
        }

        LocalDateTime now = LocalDateTime.now();
        // 保留首条记录作为接口返回值（兼容现有返回结构）
        TransferEvent first = null;
        for (TransferEvent te : pending) {
            if (first == null) {
                first = te;
            }
            // 物流签收后需同步更新对应组装记录（货权与库存状态）
            AssemblyRecord ar = assemblyRecordService.listBySn(te.getSn());
            if (ar == null) {
                throw new BusinessException("组装记录异常，SN: " + te.getSn());
            }
            // 标记物流记录已签收，并记录实际到达时间
            te.setStatus("RECEIVED");
            te.setActualArrival(now);
            // 生成收货事件摘要并上链锚定，确保签收行为可追溯
            String recvPayload = te.getSn() + "|" + te.getTrackingNumber() + "|"
                    + te.getSenderId() + "|" + receiverId + "|RECEIVED|" + now;
            te.setReceiveTxHash(blockchainAnchorService.anchor(
                    "TRANSFER_RECEIVE", HashUtil.sha256Hex(recvPayload)));
            updateById(te);
            // 同步货权归属与库存状态：收货方成为当前持有人
            ar.setCurrentHolderId(receiverId);
            ar.setStatus("IN_STOCK");
            assemblyRecordService.updateById(ar);
        }
        return first;
    }

    @Override
    public IPage<TransferEvent> listBySender(Long senderId, Page<TransferEvent> page) {
        return page(page, new LambdaQueryWrapper<TransferEvent>()
                .eq(TransferEvent::getSenderId, senderId)
                .orderByDesc(TransferEvent::getCreateTime));
    }

    @Override
    public IPage<TransferEvent> listByReceiver(Long receiverId, Page<TransferEvent> page) {
        return page(page, new LambdaQueryWrapper<TransferEvent>()
                .eq(TransferEvent::getReceiverId, receiverId)
                .orderByDesc(TransferEvent::getCreateTime));
    }

    @Override
    public IPage<TransferEvent> listForParticipant(Long userId, Page<TransferEvent> page) {
        return page(page, new LambdaQueryWrapper<TransferEvent>()
                .and(q -> q.eq(TransferEvent::getSenderId, userId).or().eq(TransferEvent::getReceiverId, userId))
                .orderByDesc(TransferEvent::getCreateTime));
    }

    @Override
    public List<TransferEvent> listBySn(String sn) {
        return list(new LambdaQueryWrapper<TransferEvent>()
                .eq(TransferEvent::getSn, sn)
                .orderByAsc(TransferEvent::getShipTime)
                .orderByAsc(TransferEvent::getCreateTime));
    }
}
