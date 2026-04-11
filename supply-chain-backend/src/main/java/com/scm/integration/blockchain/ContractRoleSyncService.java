package com.scm.integration.blockchain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scm.module.system.entity.SysRole;
import com.scm.module.system.entity.SysUser;
import com.scm.module.system.mapper.SysRoleMapper;
import com.scm.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractRoleSyncService {

    private final ObjectProvider<FiscoBcosBlockchainAnchorService> fiscoProvider;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;

    public void syncUserRoleToChain(Long userId) {
        if (userId == null) {
            return;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getBlockchainAddr())) {
            log.warn("Skip role sync: user {} missing or no blockchainAddr", userId);
            return;
        }
        SysRole role = resolvePrimaryRole(userId);
        if (role == null || !StringUtils.hasText(role.getRoleKey())) {
            log.warn("Skip role sync: user {} has no role", userId);
            return;
        }
        Integer contractRole = mapRoleKey(role.getRoleKey());
        if (contractRole == null) {
            log.info("Skip role sync: role {} not mapped to contract", role.getRoleKey());
            return;
        }
        setChainRole(user.getBlockchainAddr().trim(), contractRole, userId, role.getRoleKey());
    }

    public void clearUserRoleOnChain(Long userId) {
        if (userId == null) {
            return;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getBlockchainAddr())) {
            return;
        }
        setChainRole(user.getBlockchainAddr().trim(), 0, userId, "NONE");
    }

    public Map<String, Object> checkUserRoleConsistency(Long userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            out.put("ok", false);
            out.put("message", "user missing");
            return out;
        }
        out.put("username", user.getUsername());
        out.put("address", user.getBlockchainAddr());
        SysRole role = resolvePrimaryRole(userId);
        String roleKey = role != null ? role.getRoleKey() : null;
        Integer expect = expectedContractRole(roleKey);
        out.put("systemRoleKey", roleKey);
        out.put("expectedContractRole", expect);

        Integer chainRole = getChainRoleOfAddress(user.getBlockchainAddr());
        out.put("chainRole", chainRole);
        out.put("consistent", chainRole != null && chainRole.intValue() == expect.intValue());
        String chainErr = getChainRoleQueryError(user.getBlockchainAddr());
        if (chainErr != null) {
            out.put("message", chainErr);
        }
        out.put("ok", true);
        return out;
    }

    public Map<String, Object> repairUsersRoleConsistency(List<Long> userIds, boolean onlyInconsistent) {
        Map<String, Object> out = new LinkedHashMap<>();
        int scanned = 0;
        int repaired = 0;
        int skipped = 0;
        int failed = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        if (userIds == null) {
            userIds = new ArrayList<>();
        }
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            scanned++;
            Map<String, Object> row = checkUserRoleConsistency(userId);
            row.put("action", "NONE");
            row.put("repairOk", true);
            try {
                boolean consistent = Boolean.TRUE.equals(row.get("consistent"));
                if (onlyInconsistent && consistent) {
                    skipped++;
                    row.put("action", "SKIP_CONSISTENT");
                } else {
                    applyExpectedRoleToChain(userId);
                    repaired++;
                    row.put("action", "REPAIRED");
                    Map<String, Object> after = checkUserRoleConsistency(userId);
                    row.put("afterChainRole", after.get("chainRole"));
                    row.put("afterConsistent", after.get("consistent"));
                }
            } catch (Exception ex) {
                failed++;
                row.put("repairOk", false);
                row.put("action", "FAILED");
                row.put("error", ex.getMessage());
            }
            details.add(row);
        }
        out.put("scanned", scanned);
        out.put("repaired", repaired);
        out.put("skipped", skipped);
        out.put("failed", failed);
        out.put("details", details);
        return out;
    }

    private void setChainRole(String blockchainAddr, int contractRole, Long userId, String roleKey) {
        FiscoBcosBlockchainAnchorService fisco = fiscoProvider.getIfAvailable();
        if (fisco == null || !fisco.isAvailable()) {
            throw new IllegalStateException("FISCO not available for role sync");
        }

        try {
            List<Object> params = new ArrayList<>();
            params.add(blockchainAddr);
            params.add((long) contractRole);
            String tx = fisco.sendTransaction("setRole", params);
            log.info("Contract role synced: userId={} roleKey={} contractRole={} tx={}",
                    userId, roleKey, contractRole, tx);
        } catch (Exception e) {
            throw new RuntimeException("Sync contract role failed: " + e.getMessage(), e);
        }
    }

    private Integer getChainRoleOfAddress(String addr) {
        return getChainRoleInternal(addr, false).role;
    }

    private String getChainRoleQueryError(String addr) {
        return getChainRoleInternal(addr, true).error;
    }

    private ChainRoleResult getChainRoleInternal(String addr, boolean withError) {
        ChainRoleResult result = new ChainRoleResult();
        if (!StringUtils.hasText(addr)) {
            result.error = "empty blockchain address";
            return result;
        }
        FiscoBcosBlockchainAnchorService fisco = fiscoProvider.getIfAvailable();
        if (fisco == null || !fisco.isAvailable()) {
            result.error = "fisco unavailable";
            return result;
        }
        try {
            List<Object> params = new ArrayList<>();
            params.add(addr.trim());
            List<Object> ret = fisco.callContract("roleOf", params);
            if (ret == null || ret.isEmpty() || ret.get(0) == null) {
                result.error = "roleOf returned empty";
                return result;
            }
            Object v = ret.get(0);
            // FISCO may return BigInteger, numeric string, or map-like wrapper.
            if (v instanceof Number) {
                result.role = ((Number) v).intValue();
                return result;
            }
            if (v instanceof java.util.Map) {
                java.util.Map<?, ?> m = (java.util.Map<?, ?>) v;
                Object mv = m.get("0");
                if (mv == null && !m.isEmpty()) {
                    mv = m.values().iterator().next();
                }
                if (mv instanceof Number) {
                    result.role = ((Number) mv).intValue();
                    return result;
                }
                if (mv != null) {
                    result.role = parseIntSafe(String.valueOf(mv));
                    if (result.role != null) {
                        return result;
                    }
                }
            }
            result.role = parseIntSafe(String.valueOf(v));
            if (result.role == null) {
                result.error = "cannot parse roleOf return: " + String.valueOf(v);
            }
            return result;
        } catch (Exception e) {
            result.error = withError ? ("roleOf query failed: " + e.getMessage()) : null;
            return result;
        }
    }

    private Integer parseIntSafe(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = raw.trim().replaceAll("[^0-9-]", "");
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        try {
            return Integer.parseInt(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    private static class ChainRoleResult {
        Integer role;
        String error;
    }

    private SysRole resolvePrimaryRole(Long userId) {
        return sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .inSql(SysRole::getId, "SELECT role_id FROM sys_user_role WHERE user_id = " + userId)
                .orderByAsc(SysRole::getSortOrder)
                .last("LIMIT 1"));
    }

    private void applyExpectedRoleToChain(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getBlockchainAddr())) {
            throw new IllegalStateException("user missing or no blockchain address");
        }
        SysRole role = resolvePrimaryRole(userId);
        String roleKey = role != null ? role.getRoleKey() : null;
        Integer expect = expectedContractRole(roleKey);
        setChainRole(user.getBlockchainAddr().trim(), expect, userId, roleKey != null ? roleKey : "NONE");
    }

    private Integer expectedContractRole(String roleKey) {
        if (!StringUtils.hasText(roleKey)) {
            return 0;
        }
        Integer mapped = mapRoleKey(roleKey);
        return mapped != null ? mapped : 0;
    }

    private Integer mapRoleKey(String roleKey) {
        String k = roleKey.trim().toLowerCase();
        if ("supplier".equals(k)) return 1;
        if ("manufacturer".equals(k)) return 2;
        if ("assembler".equals(k)) return 3;
        if ("distributor".equals(k)) return 4;
        if ("regulator".equals(k)) return 5;
        return null;
    }
}
