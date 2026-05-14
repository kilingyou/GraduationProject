package com.scm.module.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.Constants;
import com.scm.common.exception.BusinessException;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.SmartContractInvokeService;
import com.scm.module.supplier.entity.Bom;
import com.scm.module.supplier.entity.DesignDocument;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.mapper.ProductionRequestMapper;
import com.scm.module.supplier.service.BomService;
import com.scm.module.supplier.service.DesignDocumentService;
import com.scm.module.supplier.service.ProductionRequestService;
import com.scm.module.system.entity.SysSupplierAudit;
import com.scm.module.system.entity.SysUser;
import com.scm.module.system.mapper.SysSupplierAuditMapper;
import com.scm.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionRequestServiceImpl extends ServiceImpl<ProductionRequestMapper, ProductionRequest>
        implements ProductionRequestService {

    private final SmartContractInvokeService smartContractInvokeService;
    private final SysSupplierAuditMapper sysSupplierAuditMapper;
    private final SysUserMapper sysUserMapper;
    private final BomService bomService;
    private final DesignDocumentService designDocumentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionRequest createOrder(ProductionRequest request) {
        // 业务前置校验 1：
        // 按创建时间倒序查询该供应商最近一条资质审核记录（只取最新一条）
        // 规则来源：供应商资质审核通过后，才允许发布生产订单
        SysSupplierAudit audit = sysSupplierAuditMapper.selectOne(new LambdaQueryWrapper<SysSupplierAudit>()
                .eq(SysSupplierAudit::getUserId, request.getSupplierId())
                .orderByDesc(SysSupplierAudit::getCreateTime)
                .last("LIMIT 1"));
        // 若没有审核记录，或者最新审核状态不是 APPROVED，则直接拒绝下单
        if (audit == null || !"APPROVED".equalsIgnoreCase(audit.getAuditStatus())) {
            throw new BusinessException("供应商资质未审核通过，无法发布生产订单");
        }
        // 业务前置校验 2：必须选择 BOM（生产所需物料清单）
        if (request.getBomId() == null) {
            throw new BusinessException("请选择 BOM");
        }
        // 根据 bomId 查询 BOM 主记录
        Bom bom = bomService.getById(request.getBomId());
        // 安全校验：BOM 必须存在，且该 BOM 必须属于当前下单供应商，防止越权使用他人 BOM
        if (bom == null || !request.getSupplierId().equals(bom.getSupplierId())) {
            throw new BusinessException("BOM 不存在或无权使用");
        }
        // 将 BOM 关联的设计文档 ID 回填到订单中，保证订单与 BOM/图纸关系一致
        request.setDesignDocId(bom.getDesignDocId());
        // 如果 BOM 关联了设计文档，则进一步查询文档并回填文档哈希
        // 该哈希后续可用于链上存证、完整性校验或跨系统追溯
        if (bom.getDesignDocId() != null) {
            DesignDocument doc = designDocumentService.getById(bom.getDesignDocId());
            if (doc != null) {
                request.setDesignDocHash(doc.getFileHash());
            }
        }
        // 校验目标生产厂商是否已完成链上角色注册/就绪
        // 若未就绪，该方法内部应抛出异常中断流程
        ensureTargetManufacturerChainReady(request.getTargetManufacturer());

        // 生成业务订单号（去掉 UUID 中的 '-'，得到更紧凑的字符串）
        request.setOrderId(UUID.randomUUID().toString().replace("-", ""));
        // 新建订单初始状态：待接单
        request.setStatus(Constants.PENDING_ACCEPTANCE);
        // 先落库，获得数据库主键及持久化快照
        save(request);
        // 对质量要求文本做哈希，避免将原文直接上链（减小链上数据、保护敏感内容）
        String qualityReqHash = hashQualityRequirementText(request.getQualityRequirement());
        request.setTxHash(smartContractInvokeService.createProductionRequest(
                request.getOrderId(),
                request.getTargetManufacturer(),
                bom.getFileHash(),
                request.getQuantity(),
                request.getDesignDocHash(),
                request.getExpectedDelivery(),
                qualityReqHash
        ));
        // 合约 create 后默认状态为 CREATED；
        // 为与数据库中的“待接单(PENDING_ACCEPTANCE)”语义保持一致，立即同步更新合约状态
        smartContractInvokeService.updateProductionRequestStatus(request.getOrderId(), Constants.PENDING_ACCEPTANCE);
        // 将 txHash 等链上结果写回数据库订单记录
        updateById(request);
        // 记录关键审计日志
        log.info("Production order created: id={}, orderId={}", request.getId(), request.getOrderId());
        // 返回包含完整信息的订单对象
        return request;
    }

    @Override
    public IPage<ProductionRequest> listBySupplier(Long supplierId, Page<ProductionRequest> page, String status) {
        LambdaQueryWrapper<ProductionRequest> wrapper = new LambdaQueryWrapper<ProductionRequest>()
                .eq(ProductionRequest::getSupplierId, supplierId)
                .eq(StringUtils.hasText(status), ProductionRequest::getStatus, status)
                .orderByDesc(ProductionRequest::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public IPage<ProductionRequest> listForManufacturer(Long manufacturerId, Page<ProductionRequest> page) {
        LambdaQueryWrapper<ProductionRequest> wrapper = new LambdaQueryWrapper<ProductionRequest>()
                .and(w -> w.eq(ProductionRequest::getTargetManufacturer, manufacturerId)
                        .or()
                        .isNull(ProductionRequest::getTargetManufacturer))
                .orderByDesc(ProductionRequest::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderBySupplier(Long orderDbId, Long supplierId) {
        ProductionRequest order = getById(orderDbId);
        if (order == null || !supplierId.equals(order.getSupplierId())) {
            throw new BusinessException("订单不存在");
        }
        if (!Constants.PENDING_ACCEPTANCE.equals(order.getStatus())) {
            throw new BusinessException("仅待接单状态的订单可撤销");
        }
        smartContractInvokeService.updateProductionRequestStatus(order.getOrderId(), Constants.CANCELLED);
        update(new LambdaUpdateWrapper<ProductionRequest>()
                .eq(ProductionRequest::getId, orderDbId)
                .set(ProductionRequest::getStatus, Constants.CANCELLED));
        log.info("Production order cancelled by supplier: id={}, orderId={}", orderDbId, order.getOrderId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void designateAssemblyAssembler(Long orderDbId, Long supplierId, Long assemblyAssemblerUserId) {
        ProductionRequest order = getById(orderDbId);
        if (order == null || !supplierId.equals(order.getSupplierId())) {
            throw new BusinessException("订单不存在");
        }
        if (assemblyAssemblerUserId != null) {
            SysUser u = sysUserMapper.selectById(assemblyAssemblerUserId);
            if (u == null) {
                throw new BusinessException("组装商用户不存在");
            }
        }
        update(new LambdaUpdateWrapper<ProductionRequest>()
                .eq(ProductionRequest::getId, orderDbId)
                .eq(ProductionRequest::getSupplierId, supplierId)
                .set(ProductionRequest::getAssemblyAssemblerId, assemblyAssemblerUserId));
    }

    @Override
    public List<ProductionRequest> listAssemblyEligibleOrders(Long assemblerUserId) {
        if (assemblerUserId == null) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<ProductionRequest>()
                .ne(ProductionRequest::getStatus, Constants.CANCELLED)
                .and(w -> w.isNull(ProductionRequest::getAssemblyAssemblerId)
                        .or()
                        .eq(ProductionRequest::getAssemblyAssemblerId, assemblerUserId))
                .orderByDesc(ProductionRequest::getCreateTime));
    }

    private void ensureTargetManufacturerChainReady(Long targetManufacturerUserId) {
        if (targetManufacturerUserId == null) {
            return;
        }
        SysUser user = sysUserMapper.selectById(targetManufacturerUserId);
        if (user == null) {
            throw new BusinessException("目标制造商不存在");
        }
        String addr = user.getBlockchainAddr();
        if (!StringUtils.hasText(addr) || !addr.trim().matches("^0x[0-9a-fA-F]{40}$")) {
            throw new BusinessException("定向生产订单要求目标制造商已配置有效链上地址");
        }
    }

    private static String hashQualityRequirementText(String qualityRequirement) {
        if (!StringUtils.hasText(qualityRequirement)) {
            return "";
        }
        return HashUtil.sha256Hex(qualityRequirement.trim());
    }
}
