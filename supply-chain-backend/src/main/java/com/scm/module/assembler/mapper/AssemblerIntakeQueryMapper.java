package com.scm.module.assembler.mapper;

import com.scm.module.assembler.dto.AvailableAssemblyEcidItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 只读查询：与 {@link com.scm.module.assembler.service.AssemblerIntakeService#verifyEcid} 通过条件对齐。
 */
@Mapper
public interface AssemblerIntakeQueryMapper {

    @Select("<script>"
            + "SELECT COUNT(*) FROM bus_device_record d "
            + "WHERE d.status = #{status} AND d.chain_registered = 1 "
            + "AND NOT EXISTS (SELECT 1 FROM bus_assembly_record ar "
            + "WHERE JSON_CONTAINS(ar.ecid_list, JSON_QUOTE(d.ecid), '$')) "
            + "<if test='keyword != null and keyword != \"\"'>AND d.ecid LIKE CONCAT('%', #{keyword}, '%')</if>"
            + "</script>")
    long countAvailableForAssembly(@Param("status") String status, @Param("keyword") String keyword);

    @Select("<script>"
            + "SELECT d.ecid, d.device_type AS deviceType, d.batch_id AS batchId "
            + "FROM bus_device_record d "
            + "WHERE d.status = #{status} AND d.chain_registered = 1 "
            + "AND NOT EXISTS (SELECT 1 FROM bus_assembly_record ar "
            + "WHERE JSON_CONTAINS(ar.ecid_list, JSON_QUOTE(d.ecid), '$')) "
            + "<if test='keyword != null and keyword != \"\"'>AND d.ecid LIKE CONCAT('%', #{keyword}, '%')</if>"
            + "ORDER BY d.create_time DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<AvailableAssemblyEcidItem> listAvailableForAssembly(
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("limit") long limit);
}
