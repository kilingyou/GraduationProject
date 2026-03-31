package com.scm.module.assembler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scm.common.Constants;
import com.scm.module.assembler.dto.IntakeVerifyResult;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.mapper.AssemblyRecordMapper;
import com.scm.module.assembler.service.AssemblerIntakeService;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.service.DeviceRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssemblerIntakeServiceImpl implements AssemblerIntakeService {

    private final DeviceRecordService deviceRecordService;
    private final AssemblyRecordMapper assemblyRecordMapper;

    @Override
    public IntakeVerifyResult verifyEcid(String ecid) {
        IntakeVerifyResult r = new IntakeVerifyResult();
        r.setEcid(ecid);
        if (!StringUtils.hasText(ecid)) {
            return r.setStatus(IntakeVerifyResult.REJECT).setMessage("ECID 为空");
        }
        String trimmed = ecid.trim();

        DeviceRecord device = deviceRecordService.getOne(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getEcid, trimmed));
        if (device == null) {
            return r.setStatus(IntakeVerifyResult.NOT_FOUND).setMessage("未找到 ECID，可能为伪劣标识");
        }

        AssemblyRecord bound = assemblyRecordMapper.findOneByContainingEcid(trimmed);
        if (bound != null) {
            return r.setStatus(IntakeVerifyResult.REJECT)
                    .setMessage("该部件已绑定整机，禁止重复组装")
                    .setBoundToSn(bound.getSn())
                    .setDeviceType(device.getDeviceType())
                    .setManufacturerBatchId(device.getBatchId())
                    .setChainRegistered(device.getChainRegistered());
        }

        if (Constants.REJECTED.equals(device.getStatus()) || Constants.QC_FAILED.equals(device.getStatus())) {
            return r.setStatus(IntakeVerifyResult.REJECT)
                    .setMessage("该部件已质检不合格并作废，禁止进入组装")
                    .setDeviceType(device.getDeviceType());
        }
        if (!Constants.QC_PASS.equals(device.getStatus())) {
            return r.setStatus(IntakeVerifyResult.REJECT)
                    .setMessage("部件未通过制造商质检，当前状态: " + device.getStatus())
                    .setDeviceType(device.getDeviceType());
        }
        if (device.getChainRegistered() == null || device.getChainRegistered() != 1) {
            return r.setStatus(IntakeVerifyResult.REJECT)
                    .setMessage("部件未完成链上注册，禁止用于组装")
                    .setDeviceType(device.getDeviceType())
                    .setManufacturerBatchId(device.getBatchId());
        }

        return r.setStatus(IntakeVerifyResult.PASS)
                .setMessage("验证通过，可用于组装")
                .setDeviceType(device.getDeviceType())
                .setManufacturerBatchId(device.getBatchId())
                .setChainRegistered(device.getChainRegistered());
    }

    @Override
    public List<IntakeVerifyResult> verifyEcids(List<String> ecids) {
        if (ecids == null || ecids.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> distinct = ecids.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        List<IntakeVerifyResult> out = new ArrayList<>(distinct.size());
        for (String e : distinct) {
            out.add(verifyEcid(e));
        }
        return out;
    }
}
