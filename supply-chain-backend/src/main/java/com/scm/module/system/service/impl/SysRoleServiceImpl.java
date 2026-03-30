package com.scm.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.module.system.entity.SysRole;
import com.scm.module.system.mapper.SysRoleMapper;
import com.scm.module.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<SysRole> listAllRoles() {
        return baseMapper.selectList(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getStatus, 1).orderByAsc(SysRole::getSortOrder));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", roleId);
        if (menuIds != null && !menuIds.isEmpty()) {
            String sql = "INSERT INTO sys_role_menu(role_id, menu_id) VALUES(?, ?)";
            jdbcTemplate.batchUpdate(sql, menuIds, menuIds.size(),
                    (ps, menuId) -> {
                        ps.setLong(1, roleId);
                        ps.setLong(2, menuId);
                    });
        }
    }
}
