package com.scm.module.manufacturer.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scm.common.Constants;
import com.scm.common.Result;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.entity.ManufacturingAgreement;
import com.scm.module.manufacturer.entity.ProductionBatch;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.manufacturer.service.ManufacturingAgreementService;
import com.scm.module.manufacturer.service.ProductionBatchService;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.service.ProductionRequestService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manufacturer/dashboard")
@RequiredArgsConstructor
public class ManufacturerDashboardController {

    private final ProductionBatchService productionBatchService;
    private final DeviceRecordService deviceRecordService;
    private final ManufacturingAgreementService manufacturingAgreementService;
    private final ProductionRequestService productionRequestService;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        LoginUser user = currentUser();
        Long mid = user.getUserId();

        long batchCount = productionBatchService.count(new LambdaQueryWrapper<ProductionBatch>()
                .eq(ProductionBatch::getManufacturerId, mid));
        long deviceTotal = deviceRecordService.count(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getManufacturerId, mid));
        long qcPass = deviceRecordService.count(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getManufacturerId, mid)
                .eq(DeviceRecord::getStatus, "QC_PASS"));
        long rejected = deviceRecordService.count(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getManufacturerId, mid)
                .eq(DeviceRecord::getStatus, "REJECTED"));
        long relatedOrders = manufacturingAgreementService.count(
                new LambdaQueryWrapper<ManufacturingAgreement>()
                        .eq(ManufacturingAgreement::getManufacturerId, mid));
        long ordersCompleted = productionRequestService.count(
                new LambdaQueryWrapper<ProductionRequest>()
                        .eq(ProductionRequest::getStatus, Constants.COMPLETED)
                        .apply("EXISTS (SELECT 1 FROM bus_manufacturing_agreement ma WHERE ma.order_id = bus_production_request.order_id AND ma.manufacturer_id = {0})",
                                mid));
        long batchesCompleted = productionBatchService.count(
                new LambdaQueryWrapper<ProductionBatch>()
                        .eq(ProductionBatch::getManufacturerId, mid)
                        .eq(ProductionBatch::getStatus, "COMPLETED"));

        double passRate = deviceTotal > 0 ? (qcPass * 100.0 / deviceTotal) : 0;
        double orderDoneRate = relatedOrders > 0 ? (ordersCompleted * 100.0 / relatedOrders) : 0;

        List<Long> last7 = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = LocalDateTime.of(day.plusDays(1), LocalTime.MIN);
            long c = deviceRecordService.count(new LambdaQueryWrapper<DeviceRecord>()
                    .eq(DeviceRecord::getManufacturerId, mid)
                    .ge(DeviceRecord::getCreateTime, start)
                    .lt(DeviceRecord::getCreateTime, end));
            last7.add(c);
        }

        Map<String, Object> pie = new HashMap<>();
        pie.put("qcPass", qcPass);
        pie.put("rejected", rejected);
        pie.put("other", Math.max(0, deviceTotal - qcPass - rejected));

        Map<String, Object> data = new HashMap<>();
        data.put("productionBatches", batchCount);
        data.put("relatedOrders", relatedOrders);
        data.put("ordersCompleted", ordersCompleted);
        data.put("batchesCompleted", batchesCompleted);
        data.put("orderCompletionRatePercent", Math.round(orderDoneRate * 10) / 10.0);
        data.put("deviceTotal", deviceTotal);
        data.put("qcPass", qcPass);
        data.put("rejected", rejected);
        data.put("passRatePercent", Math.round(passRate * 10) / 10.0);
        data.put("last7DaysNewDevices", last7);
        data.put("qualityPie", pie);
        return Result.ok(data);
    }

    @GetMapping("/device-lookup")
    public Result<DeviceRecord> deviceLookup(@RequestParam String ecid) {
        LoginUser user = currentUser();
        DeviceRecord d = deviceRecordService.getOne(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getEcid, ecid)
                .eq(DeviceRecord::getManufacturerId, user.getUserId()));
        if (d == null) {
            return Result.fail("未找到该 ECID 或无权查看");
        }
        return Result.ok(d);
    }

    private LoginUser currentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
