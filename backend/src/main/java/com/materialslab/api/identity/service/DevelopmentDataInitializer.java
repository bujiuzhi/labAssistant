package com.materialslab.api.identity.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 为开发测试数据库初始化可追溯的账户、项目和实验种子数据。 */
@Component
@Profile("dev")
public class DevelopmentDataInitializer implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DevelopmentDataInitializer.class);
    private static final UUID ORGANIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("10000000-0000-0000-0000-000000000101");
    private static final UUID MANAGER_ID = UUID.fromString("10000000-0000-0000-0000-000000000102");
    private static final UUID RESEARCHER_ID = UUID.fromString("10000000-0000-0000-0000-000000000103");
    private static final UUID ADMIN_ROLE_ID = UUID.fromString("10000000-0000-0000-0000-000000000201");
    private static final UUID MANAGER_ROLE_ID = UUID.fromString("10000000-0000-0000-0000-000000000202");
    private static final UUID RESEARCHER_ROLE_ID = UUID.fromString("10000000-0000-0000-0000-000000000203");
    private static final UUID POLYMER_PROJECT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ENERGY_PROJECT_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID SCALE_PROJECT_ID = UUID.fromString("20000000-0000-0000-0000-000000000003");

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String developmentPassword;

    public DevelopmentDataInitializer(
            DataSource dataSource,
            PasswordEncoder passwordEncoder,
            @Value("${materials-lab.development-password}") String developmentPassword) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.passwordEncoder = passwordEncoder;
        this.developmentPassword = developmentPassword;
    }

    /** 在开发测试库为空时写入测试业务数据，不覆盖已有数据。 */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organization WHERE organization_code = 'DEV_TEST'", Long.class) > 0) {
            LOGGER.info("开发测试数据库已存在 DEV_TEST 种子数据，跳过初始化");
            return;
        }

        createOrganizationAndUsers();
        createProjects();
        createExperiments();
        jdbcTemplate.update(
                "INSERT INTO project_follow(id, project_id, user_id) VALUES (?, ?, ?)",
                UUID.fromString("30000000-0000-0000-0000-000000000001"), POLYMER_PROJECT_ID, ADMIN_ID);
        LOGGER.info("开发测试数据库已初始化 DEV_TEST 种子数据：3 个账户、3 个项目、6 个实验");
    }

    private void createOrganizationAndUsers() {
        jdbcTemplate.update(
                "INSERT INTO organization(id, organization_code, name) VALUES (?, ?, ?)",
                ORGANIZATION_ID, "DEV_TEST", "材料实验助手测试组织");
        createUser(ADMIN_ID, "admin", "测试管理员", true);
        createUser(MANAGER_ID, "manager", "测试项目管理员", false);
        createUser(RESEARCHER_ID, "researcher", "测试实验员", false);
        createRole(ADMIN_ROLE_ID, "super_admin", "超级管理员", ADMIN_ID);
        createRole(MANAGER_ROLE_ID, "project_manager", "项目管理员", MANAGER_ID);
        createRole(RESEARCHER_ROLE_ID, "researcher", "实验员", RESEARCHER_ID);
    }

    private void createUser(UUID userId, String username, String displayName, boolean superAdmin) {
        jdbcTemplate.update("""
                INSERT INTO user_account(
                  id, organization_id, password, username, display_name, is_super_admin, is_superuser, is_staff)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, ORGANIZATION_ID, passwordEncoder.encode(developmentPassword), username, displayName,
                superAdmin, superAdmin, superAdmin);
    }

    private void createRole(UUID roleId, String roleCode, String roleName, UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO role(id, organization_id, role_code, name, description, is_system)
                VALUES (?, ?, ?, ?, ?, ?)
                """, roleId, ORGANIZATION_ID, roleCode, roleName, "开发测试角色", true);
        jdbcTemplate.update("""
                INSERT INTO user_role(id, organization_id, user_id, role_id, created_by_id)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), ORGANIZATION_ID, userId, roleId, ADMIN_ID);
    }

    private void createProjects() {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Shanghai"));
        createProject(POLYMER_PROJECT_ID, "PRJ-TEST-001", "高耐热聚酰亚胺薄膜配方验证", "聚酰亚胺", "配方筛选与耐热性能验证",
                "中试验证", 68, "active", MANAGER_ID, now.minusDays(42), now.plusDays(21),
                "[\"完成单体配比筛选\",\"验证 300℃ 热稳定性\"]",
                "[{\"name\":\"中试样品制备\",\"date\":\"2026-08-18\",\"state\":\"current\"}]");
        createProject(ENERGY_PROJECT_ID, "PRJ-TEST-002", "高倍率锂电池硅碳负极优化", "新能源材料", "硅碳负极循环性能测试",
                "性能评估", 45, "at_risk", RESEARCHER_ID, now.minusDays(30), now.plusDays(4),
                "[\"优化首效与循环寿命\",\"完成倍率性能测试\"]",
                "[{\"name\":\"循环寿命测试\",\"date\":\"2026-08-07\",\"state\":\"current\"}]");
        createProject(SCALE_PROJECT_ID, "PRJ-TEST-003", "低 VOC 环氧树脂放大试验", "环氧树脂", "低挥发性环氧树脂工艺放大",
                "方案设计", 20, "not_started", MANAGER_ID, now.minusDays(5), now.plusDays(55),
                "[\"建立放大工艺窗口\"]",
                "[{\"name\":\"试验方案评审\",\"date\":\"2026-08-28\",\"state\":\"todo\"}]");
    }

    private void createProject(
            UUID projectId, String projectNo, String name, String projectType, String description, String stage,
            int progress, String status, UUID ownerId, OffsetDateTime startDate, OffsetDateTime endDate,
            String objectives, String milestones) {
        jdbcTemplate.update("""
                INSERT INTO project(
                  id, organization_id, project_no, name, project_type_code, description, current_stage,
                  progress_percent, document_count, experiment_count, data_resource_count, objectives, milestones,
                  status, owner_id, planned_start_date, planned_end_date, created_by_id, updated_by_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?, ?)
                """, projectId, ORGANIZATION_ID, projectNo, name, projectType, description, stage, progress,
                objectives, milestones, status, ownerId, startDate, endDate, ADMIN_ID, ADMIN_ID, startDate, startDate);
    }

    private void createExperiments() {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Shanghai"));
        createExperiment("EXP-TEST-001", POLYMER_PROJECT_ID, "PI-6F 配方热稳定性验证", "聚酰亚胺", "性能评估", "completed", MANAGER_ID, now.minusDays(25));
        createExperiment("EXP-TEST-002", POLYMER_PROJECT_ID, "PI-6F 薄膜拉伸性能复测", "聚酰亚胺", "性能评估", "in_progress", RESEARCHER_ID, now.minusDays(12));
        createExperiment("EXP-TEST-003", ENERGY_PROJECT_ID, "硅碳负极首效测试", "新能源材料", "性能评估", "completed", RESEARCHER_ID, now.minusDays(18));
        createExperiment("EXP-TEST-004", ENERGY_PROJECT_ID, "硅碳负极 1C 循环寿命测试", "新能源材料", "性能评估", "in_progress", RESEARCHER_ID, now.minusDays(6));
        createExperiment("EXP-TEST-005", ENERGY_PROJECT_ID, "硅碳负极倍率性能测试", "新能源材料", "性能评估", "not_started", MANAGER_ID, now.minusDays(2));
        createExperiment("EXP-TEST-006", SCALE_PROJECT_ID, "低 VOC 环氧树脂黏度窗口测定", "环氧树脂", "方案设计", "not_started", MANAGER_ID, now.minusDays(1));
    }

    private void createExperiment(
            String experimentNo, UUID projectId, String name, String experimentType, String phase, String status,
            UUID ownerId, OffsetDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO experiment(
                  id, organization_id, project_id, experiment_no, name, experiment_type, phase, status, purpose,
                  estimated_start, estimated_end, started_at, completed_at, owner_id, created_by_id, updated_by_id,
                  created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), ORGANIZATION_ID, projectId, experimentNo, name, experimentType, phase, status,
                "开发测试数据，用于验证真实数据库查询与接口展示", createdAt, createdAt.plusDays(7),
                "in_progress".equals(status) || "completed".equals(status) ? createdAt.plusDays(1) : null,
                "completed".equals(status) ? createdAt.plusDays(4) : null, ownerId, ADMIN_ID, ADMIN_ID, createdAt, createdAt);
    }
}
