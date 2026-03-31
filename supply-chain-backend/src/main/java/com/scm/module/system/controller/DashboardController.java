package com.scm.module.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scm.common.Result;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.service.AssemblyBatchService;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.module.distributor.entity.SalesRecord;
import com.scm.module.distributor.entity.TransferEvent;
import com.scm.module.distributor.service.SalesRecordService;
import com.scm.module.distributor.service.TransferEventService;
import com.scm.module.enduser.entity.Decommission;
import com.scm.module.enduser.entity.RecallRequest;
import com.scm.module.enduser.entity.UserProduct;
import com.scm.module.enduser.service.DecommissionService;
import com.scm.module.enduser.service.RecallRequestService;
import com.scm.module.enduser.service.UserProductService;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.entity.ProductionBatch;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.manufacturer.service.ProductionBatchService;
import com.scm.module.regulator.service.InspectionTaskService;
import com.scm.module.regulator.service.RecallNoticeService;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.service.DesignDocumentService;
import com.scm.module.supplier.service.ProductionRequestService;
import com.scm.module.system.service.SysUserService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final SysUserService sysUserService;
    private final ProductionRequestService productionRequestService;
    private final DeviceRecordService deviceRecordService;
    private final ProductionBatchService productionBatchService;
    private final AssemblyRecordService assemblyRecordService;
    private final AssemblyBatchService assemblyBatchService;
    private final TransferEventService transferEventService;
    private final SalesRecordService salesRecordService;
    private final UserProductService userProductService;
    private final DecommissionService decommissionService;
    private final RecallRequestService recallRequestService;
    private final InspectionTaskService inspectionTaskService;
    private final RecallNoticeService recallNoticeService;
    private final DesignDocumentService designDocumentService;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        LoginUser lu = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String role = lu.getRoleKey();
        Long uid = lu.getUserId();

        Map<String, Object> data = new HashMap<>();
        switch (role != null ? role : "") {
            case "supplier":
                buildSupplierStats(data, uid);
                break;
            case "manufacturer":
                buildManufacturerStats(data, uid);
                break;
            case "assembler":
                buildAssemblerStats(data, uid);
                break;
            case "distributor":
                buildDistributorStats(data, uid);
                break;
            case "enduser":
                buildEnduserStats(data, uid);
                break;
            case "regulator":
            case "admin":
                buildRegulatorStats(data);
                break;
            default:
                buildRegulatorStats(data);
                break;
        }
        data.put("role", role);
        return Result.ok(data);
    }

    private void buildSupplierStats(Map<String, Object> data, Long uid) {
        long orders = productionRequestService.count(new LambdaQueryWrapper<ProductionRequest>()
                .eq(ProductionRequest::getSupplierId, uid));
        long completed = productionRequestService.count(new LambdaQueryWrapper<ProductionRequest>()
                .eq(ProductionRequest::getSupplierId, uid)
                .eq(ProductionRequest::getStatus, "COMPLETED"));
        long docs = designDocumentService.count();
        data.put("card1", card("生产订单", orders, "已完成 " + completed + " 单"));
        data.put("card2", card("完成率", orders > 0 ? Math.round(completed * 1000.0 / orders) / 10.0 + "%" : "0%", "订单完成率"));
        data.put("card3", card("设计文档", docs, "累计上传"));
        data.put("card4", card("在途订单", orders - completed, "进行中"));
    }

    private void buildManufacturerStats(Map<String, Object> data, Long uid) {
        long batches = productionBatchService.count(new LambdaQueryWrapper<ProductionBatch>()
                .eq(ProductionBatch::getManufacturerId, uid));
        long devices = deviceRecordService.count(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getManufacturerId, uid));
        long qcPass = deviceRecordService.count(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getManufacturerId, uid)
                .eq(DeviceRecord::getStatus, "QC_PASS"));
        long onChain = deviceRecordService.count(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getManufacturerId, uid)
                .eq(DeviceRecord::getChainRegistered, 1));
        data.put("card1", card("生产批次", batches, "累计批次"));
        data.put("card2", card("设备总数", devices, "已上链 " + onChain));
        data.put("card3", card("质检通过率", devices > 0 ? Math.round(qcPass * 1000.0 / devices) / 10.0 + "%" : "0%", "通过 " + qcPass + " 台"));
        data.put("card4", card("上链率", devices > 0 ? Math.round(onChain * 1000.0 / devices) / 10.0 + "%" : "0%", "区块链存证"));
    }

    private void buildAssemblerStats(Map<String, Object> data, Long uid) {
        long batches = assemblyBatchService.count(new LambdaQueryWrapper<com.scm.module.assembler.entity.AssemblyBatch>()
                .eq(com.scm.module.assembler.entity.AssemblyBatch::getAssemblerId, uid));
        long records = assemblyRecordService.count(new LambdaQueryWrapper<AssemblyRecord>()
                .eq(AssemblyRecord::getAssemblerId, uid));
        long pass = assemblyRecordService.count(new LambdaQueryWrapper<AssemblyRecord>()
                .eq(AssemblyRecord::getAssemblerId, uid)
                .eq(AssemblyRecord::getTestResult, "PASS"));
        long onChain = assemblyRecordService.count(new LambdaQueryWrapper<AssemblyRecord>()
                .eq(AssemblyRecord::getAssemblerId, uid)
                .eq(AssemblyRecord::getChainRegistered, 1));
        data.put("card1", card("组装批次", batches, "累计批次"));
        data.put("card2", card("组装记录", records, "已上链 " + onChain));
        data.put("card3", card("质检通过率", records > 0 ? Math.round(pass * 1000.0 / records) / 10.0 + "%" : "0%", "通过 " + pass + " 条"));
        data.put("card4", card("部件消耗", assemblyRecordService.sumEcidSlots(uid), "部件绑定总次数"));
    }

    private void buildDistributorStats(Map<String, Object> data, Long uid) {
        long sent = transferEventService.count(new LambdaQueryWrapper<TransferEvent>()
                .eq(TransferEvent::getSenderId, uid));
        long received = transferEventService.count(new LambdaQueryWrapper<TransferEvent>()
                .eq(TransferEvent::getReceiverId, uid)
                .eq(TransferEvent::getStatus, "RECEIVED"));
        long inTransit = transferEventService.count(new LambdaQueryWrapper<TransferEvent>()
                .and(w -> w.eq(TransferEvent::getSenderId, uid).or().eq(TransferEvent::getReceiverId, uid))
                .eq(TransferEvent::getStatus, "IN_TRANSIT"));
        long sales = salesRecordService.count(new LambdaQueryWrapper<SalesRecord>()
                .eq(SalesRecord::getSellerId, uid));
        data.put("card1", card("发货批次", sent, "累计发货"));
        data.put("card2", card("已收货", received, "确认收货"));
        data.put("card3", card("在途", inTransit, "运输中"));
        data.put("card4", card("销售记录", sales, "累计销售"));
    }

    private void buildEnduserStats(Map<String, Object> data, Long uid) {
        long bound = userProductService.count(new LambdaQueryWrapper<UserProduct>()
                .eq(UserProduct::getUserId, uid));
        long decommissioned = decommissionService.count(new LambdaQueryWrapper<Decommission>()
                .eq(Decommission::getApplicantId, uid));
        long complaints = recallRequestService.count(new LambdaQueryWrapper<RecallRequest>()
                .eq(RecallRequest::getUserId, uid));
        long pending = recallRequestService.count(new LambdaQueryWrapper<RecallRequest>()
                .eq(RecallRequest::getUserId, uid)
                .eq(RecallRequest::getStatus, "PENDING"));
        data.put("card1", card("绑定产品", bound, "我的产品"));
        data.put("card2", card("报废登记", decommissioned, "我的报废"));
        data.put("card3", card("投诉反馈", complaints, "待处理 " + pending + " 条"));
        data.put("card4", card("溯源查询", "∞", "随时可查"));
    }

    private void buildRegulatorStats(Map<String, Object> data) {
        long users = sysUserService.count();
        long devices = deviceRecordService.count();
        long inspections = inspectionTaskService.count();
        long recalls = recallNoticeService.count();
        data.put("card1", card("系统用户", users, "所有注册用户"));
        data.put("card2", card("设备总数", devices, "全链路设备"));
        data.put("card3", card("抽检任务", inspections, "累计任务"));
        data.put("card4", card("召回通知", recalls, "累计发布"));
    }

    private static Map<String, Object> card(String title, Object value, String desc) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        m.put("value", value);
        m.put("desc", desc);
        return m;
    }
}
