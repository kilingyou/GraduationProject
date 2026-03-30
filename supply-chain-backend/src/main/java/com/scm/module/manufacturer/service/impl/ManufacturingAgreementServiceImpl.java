package com.scm.module.manufacturer.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.module.manufacturer.entity.ManufacturingAgreement;
import com.scm.module.manufacturer.mapper.ManufacturingAgreementMapper;
import com.scm.module.manufacturer.service.ManufacturingAgreementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManufacturingAgreementServiceImpl
        extends ServiceImpl<ManufacturingAgreementMapper, ManufacturingAgreement>
        implements ManufacturingAgreementService {

    @Override
    public boolean signAgreement(ManufacturingAgreement agreement) {
        return save(agreement);
    }
}
