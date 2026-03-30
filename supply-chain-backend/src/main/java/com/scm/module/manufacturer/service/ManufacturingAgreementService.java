package com.scm.module.manufacturer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.manufacturer.entity.ManufacturingAgreement;

public interface ManufacturingAgreementService extends IService<ManufacturingAgreement> {

    boolean signAgreement(ManufacturingAgreement agreement);
}
