package com.materialslab.api.identity.mapper;

import com.materialslab.api.identity.domain.OrganizationSummary;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 组织层级查询 Mapper。 */
@Mapper
public interface OrganizationMapper {
    /** 查询当前组织，用于确认会话的数据隔离边界。 */
    @Select("""
            SELECT id, organization_code, name, parent_id, status
            FROM organization WHERE id = #{organizationId} AND status = 'active'
            """)
    OrganizationSummary findActiveById(@Param("organizationId") UUID organizationId);
}
