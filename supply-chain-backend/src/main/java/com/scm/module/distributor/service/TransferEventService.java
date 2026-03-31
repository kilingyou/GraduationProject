package com.scm.module.distributor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.distributor.entity.TransferEvent;

import java.time.LocalDateTime;
import java.util.List;

public interface TransferEventService extends IService<TransferEvent> {

    /**
     * 单件发货：校验当前登录用户为货权方、无在途单，更新整机为在途并上链锚定。
     */
    TransferEvent shipTransfer(String sn, Long senderId, Long receiverId, String logisticsCompany,
                               String trackingNumber, LocalDateTime shipTime, LocalDateTime estimatedArrival,
                               String transferType);

    /** 批量发货（共用物流单号），一单失败整批回滚 */
    List<TransferEvent> shipBatch(List<String> sns, Long senderId, Long receiverId, String logisticsCompany,
                                  String trackingNumber, LocalDateTime shipTime, LocalDateTime estimatedArrival,
                                  String transferType);

    /** 确认收货：按 transferId 或 运单号+SN 定位，货权转移给收货方 */
    TransferEvent receiveTransfer(Long receiverId, Long transferId, String trackingNumber, String sn);

    IPage<TransferEvent> listBySender(Long senderId, Page<TransferEvent> page);

    IPage<TransferEvent> listByReceiver(Long receiverId, Page<TransferEvent> page);

    /** 作为发货方或收货方参与的物流记录 */
    IPage<TransferEvent> listForParticipant(Long userId, Page<TransferEvent> page);

    List<TransferEvent> listBySn(String sn);
}
