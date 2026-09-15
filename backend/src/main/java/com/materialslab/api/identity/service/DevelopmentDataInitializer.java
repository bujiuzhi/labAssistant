package com.materialslab.api.identity.service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 为开发测试数据库写入可追溯、可重复执行的账户、项目和实验种子数据。 */
@Component
@Profile("dev & !prod")
@Order(Ordered.LOWEST_PRECEDENCE)
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

    /** 补齐缺失的开发测试数据；已有数据和人工修改后的记录均不覆盖。 */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createOrganizationAndUsers();
        createExistingProjects();
        createLegacyPrototypeProjects();
        createExistingExperiments();
        createLegacyPrototypeExperiments();
        refreshExperimentCounts();
        createProjectDocuments();
        LOGGER.info("开发测试数据库种子校验完成：保留已有数据并补齐历史原型项目、实验记录和可读取的项目文档");
    }

    private void createOrganizationAndUsers() {
        jdbcTemplate.update("""
                INSERT INTO organization(id, organization_code, name) VALUES (?, ?, ?)
                ON CONFLICT (organization_code) DO NOTHING
                """, ORGANIZATION_ID, "DEV_TEST", "材料实验助手测试组织");
        createUser(ADMIN_ID, "admin", "测试管理员", true);
        createUser(MANAGER_ID, "manager", "测试项目管理员", false);
        createUser(RESEARCHER_ID, "researcher", "测试实验员", false);
        createUser(userId("zhangwei"), "zhangwei", "张伟", false);
        createUser(userId("lina"), "lina", "李娜", false);
        createUser(userId("wangqiang"), "wangqiang", "王强", false);
        createUser(userId("chensi"), "chensi", "陈思", false);
        createUser(userId("zhouhao"), "zhouhao", "周浩", false);
        createUser(userId("liuyang"), "liuyang", "刘洋", false);
        createUser(userId("zhaomin"), "zhaomin", "赵敏", false);
        createRole(ADMIN_ROLE_ID, "super_admin", "超级管理员", ADMIN_ID);
        createRole(MANAGER_ROLE_ID, "project_manager", "项目管理员", MANAGER_ID);
        createRole(RESEARCHER_ROLE_ID, "researcher", "实验员", RESEARCHER_ID);
        assignRole(userId("zhangwei"), RESEARCHER_ROLE_ID);
        assignRole(userId("lina"), RESEARCHER_ROLE_ID);
        assignRole(userId("wangqiang"), RESEARCHER_ROLE_ID);
        assignRole(userId("chensi"), RESEARCHER_ROLE_ID);
        assignRole(userId("zhouhao"), RESEARCHER_ROLE_ID);
        assignRole(userId("liuyang"), RESEARCHER_ROLE_ID);
        assignRole(userId("zhaomin"), RESEARCHER_ROLE_ID);
        createRolePermissions();
    }

    private void createUser(UUID userId, String username, String displayName, boolean superAdmin) {
        jdbcTemplate.update("""
                INSERT INTO user_account(
                  id, organization_id, password, username, display_name, is_super_admin, is_platform_admin, is_superuser, is_staff)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (organization_id, username) DO UPDATE
                SET is_platform_admin = user_account.is_platform_admin OR EXCLUDED.is_platform_admin
                """, userId, ORGANIZATION_ID, passwordEncoder.encode(developmentPassword), username, displayName,
                superAdmin, false, superAdmin, superAdmin);
    }

    private void createRole(UUID roleId, String roleCode, String roleName, UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO role(id, organization_id, role_code, name, description, is_system)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (organization_id, role_code) DO NOTHING
                """, roleId, ORGANIZATION_ID, roleCode, roleName, "开发测试角色", true);
        assignRole(userId, roleId);
    }

    private void assignRole(UUID userId, UUID roleId) {
        jdbcTemplate.update("""
                INSERT INTO user_role(id, organization_id, user_id, role_id, created_by_id)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id, role_id) DO NOTHING
                """, stableId("user-role-" + userId + "-" + roleId), ORGANIZATION_ID, userId, roleId, ADMIN_ID);
    }

    /** 创建开发环境 RBAC 权限字典并绑定系统角色。 */
    private void createRolePermissions() {
        for (var permission : SystemIdentityCatalog.PERMISSIONS) {
            createPermission(permission.code(), permission.name(), permission.moduleCode());
        }
        for (var role : SystemIdentityCatalog.ROLES) {
            UUID roleId = switch (role.code()) {
                case "super_admin" -> ADMIN_ROLE_ID;
                case "project_manager" -> MANAGER_ROLE_ID;
                case "researcher" -> RESEARCHER_ROLE_ID;
                default -> throw new IllegalStateException("未配置开发角色 ID");
            };
            grant(roleId, role.permissions().toArray(String[]::new));
        }
    }

    private void createPermission(String code, String name, String moduleCode) {
        jdbcTemplate.update("""
                INSERT INTO permission(id, permission_code, name, module_code, description)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (permission_code) DO NOTHING
                """, stableId("permission-" + code), code, name, moduleCode, "开发测试 RBAC 权限");
    }

    private void grant(UUID roleId, String... permissionCodes) {
        for (String permissionCode : permissionCodes) {
            jdbcTemplate.update("""
                    INSERT INTO role_permission(id, role_id, permission_id)
                    VALUES (?, ?, ?)
                    ON CONFLICT (role_id, permission_id) DO NOTHING
                    """, stableId("role-permission-" + roleId + "-" + permissionCode), roleId,
                    stableId("permission-" + permissionCode));
        }
    }

    private void createExistingProjects() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(8));
        createProject(new ProjectSeed(POLYMER_PROJECT_ID, "PRJ-TEST-001", "高耐热聚酰亚胺薄膜配方验证", "聚酰亚胺",
                "配方筛选与耐热性能验证", "中试验证", 68, "active", MANAGER_ID, now.minusDays(42), now.plusDays(21),
                List.of("完成单体配比筛选", "验证 300℃ 热稳定性"), "中试样品制备", "2026-08-18", "current", 0, 0, 0));
        createProject(new ProjectSeed(ENERGY_PROJECT_ID, "PRJ-TEST-002", "高倍率锂电池硅碳负极优化", "新能源材料",
                "硅碳负极循环性能测试", "性能评估", 45, "at_risk", RESEARCHER_ID, now.minusDays(30), now.plusDays(4),
                List.of("优化首效与循环寿命", "完成倍率性能测试"), "循环寿命测试", "2026-08-07", "current", 0, 0, 0));
        createProject(new ProjectSeed(SCALE_PROJECT_ID, "PRJ-TEST-003", "低 VOC 环氧树脂放大试验", "环氧树脂",
                "低挥发性环氧树脂工艺放大", "方案设计", 20, "not_started", MANAGER_ID, now.minusDays(5), now.plusDays(55),
                List.of("建立放大工艺窗口"), "试验方案评审", "2026-08-28", "todo", 0, 0, 0));
        jdbcTemplate.update("""
                INSERT INTO project_follow(id, project_id, user_id) VALUES (?, ?, ?)
                ON CONFLICT (project_id, user_id) DO NOTHING
                """, stableId("project-follow-PRJ-TEST-001"), POLYMER_PROJECT_ID, ADMIN_ID);
    }

    /** 将历史总览原型的 20 个项目定义迁移为实际项目行，而非保留在前端。 */
    private void createLegacyPrototypeProjects() {
        for (ProjectSeed project : legacyProjectSeeds()) {
            createProject(project);
        }
    }

    private void createProject(ProjectSeed project) {
        OffsetDateTime archivedAt = "archived".equals(project.status()) ? project.endDate() : null;
        jdbcTemplate.update("""
                INSERT INTO project(
                  id, organization_id, project_no, name, project_type_code, description, current_stage,
                  progress_percent, document_count, experiment_count, data_resource_count, objectives, milestones,
                  status, owner_id, planned_start_date, planned_end_date, actual_end_at, archived_at,
                  created_by_id, updated_by_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (organization_id, project_no) DO NOTHING
                """, project.id(), ORGANIZATION_ID, project.projectNo(), project.name(), project.projectType(), project.description(),
                project.stage(), project.progress(), project.documentCount(), project.experimentCount(), project.resourceCount(),
                objectivesJson(project.objectives()), milestoneJson(project.milestoneName(), project.milestoneDate(), project.milestoneState()),
                project.status(), project.ownerId(), project.startDate(), project.endDate(), archivedAt, archivedAt,
                ADMIN_ID, ADMIN_ID, project.startDate(), project.startDate());
        jdbcTemplate.update("""
                INSERT INTO project_member(id, organization_id, project_id, user_id, member_role, joined_at, created_by_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (project_id, user_id) DO NOTHING
                """, stableId("project-member-" + project.projectNo()), ORGANIZATION_ID, project.id(), project.ownerId(), "owner",
                project.startDate(), ADMIN_ID);
        createProjectOperationLog(project);
    }

    private void createProjectOperationLog(ProjectSeed project) {
        jdbcTemplate.update("""
                INSERT INTO business_operation_log(
                  id, organization_id, actor_id, domain, object_id, object_no, action_type, description, changes, created_at)
                VALUES (?, ?, ?, 'project', ?, ?, 'created', ?, CAST(? AS jsonb), ?)
                ON CONFLICT (id) DO NOTHING
                """, stableId("project-log-" + project.projectNo()), ORGANIZATION_ID, project.ownerId(), project.id(), project.projectNo(),
                "写入开发测试项目：" + project.name(), "{\"source\":\"legacy-prototype\",\"test_data\":true}", project.startDate());
    }

    private void createExistingExperiments() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(8));
        createExperiment("EXP-TEST-001", POLYMER_PROJECT_ID, "PI-6F 配方热稳定性验证", "聚酰亚胺", "性能评估", "completed", MANAGER_ID, now.minusDays(25));
        createExperiment("EXP-TEST-002", POLYMER_PROJECT_ID, "PI-6F 薄膜拉伸性能复测", "聚酰亚胺", "性能评估", "in_progress", RESEARCHER_ID, now.minusDays(12));
        createExperiment("EXP-TEST-003", ENERGY_PROJECT_ID, "硅碳负极首效测试", "新能源材料", "性能评估", "completed", RESEARCHER_ID, now.minusDays(18));
        createExperiment("EXP-TEST-004", ENERGY_PROJECT_ID, "硅碳负极 1C 循环寿命测试", "新能源材料", "性能评估", "in_progress", RESEARCHER_ID, now.minusDays(6));
        createExperiment("EXP-TEST-005", ENERGY_PROJECT_ID, "硅碳负极倍率性能测试", "新能源材料", "性能评估", "not_started", MANAGER_ID, now.minusDays(2));
        createExperiment("EXP-TEST-006", SCALE_PROJECT_ID, "低 VOC 环氧树脂黏度窗口测定", "环氧树脂", "方案设计", "not_started", MANAGER_ID, now.minusDays(1));
    }

    /** 为每个历史项目创建与原型数量一致的实验，以及可由 ELN 接口读取的记录。 */
    private void createLegacyPrototypeExperiments() {
        for (ProjectSeed project : legacyProjectSeeds()) {
            for (int index = 1; index <= project.experimentCount(); index++) {
                String experimentNo = "EXP-" + project.projectNo().substring(4) + "-" + String.format("%02d", index);
                String status = experimentStatus(project.status(), index);
                createExperiment(experimentNo, project.id(), project.name() + "实验 " + String.format("%02d", index),
                        project.projectType(), project.stage(), status, project.ownerId(), project.startDate().plusDays(index));
            }
        }
    }

    private void createExperiment(
            String experimentNo, UUID projectId, String name, String experimentType, String phase, String status,
            UUID ownerId, OffsetDateTime createdAt) {
        UUID experimentId = stableId("experiment-" + experimentNo);
        jdbcTemplate.update("""
                INSERT INTO experiment(
                  id, organization_id, project_id, experiment_no, name, experiment_type, phase, status, purpose,
                  estimated_start, estimated_end, started_at, completed_at, owner_id, created_by_id, updated_by_id,
                  created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (organization_id, experiment_no) DO NOTHING
                """, experimentId, ORGANIZATION_ID, projectId, experimentNo, name, experimentType, phase, status,
                "开发测试数据：用于验证真实数据库查询、电子实验记录和状态流转", createdAt, createdAt.plusDays(7),
                "in_progress".equals(status) || "completed".equals(status) ? createdAt.plusDays(1) : null,
                "completed".equals(status) ? createdAt.plusDays(4) : null, ownerId, ADMIN_ID, ADMIN_ID, createdAt, createdAt);
        UUID actualExperimentId = jdbcTemplate.queryForObject(
                "SELECT id FROM experiment WHERE organization_id = ? AND experiment_no = ?", UUID.class, ORGANIZATION_ID, experimentNo);
        createExperimentRecord(actualExperimentId, experimentNo, name, status, createdAt);
        jdbcTemplate.update("""
                INSERT INTO experiment_participant(id, organization_id, experiment_id, user_id, participant_role, joined_at, created_by_id)
                VALUES (?, ?, ?, ?, 'owner', ?, ?)
                ON CONFLICT (experiment_id, user_id) DO NOTHING
                """, stableId("experiment-participant-" + experimentNo), ORGANIZATION_ID, actualExperimentId, ownerId, createdAt, ADMIN_ID);
    }

    private void createExperimentRecord(UUID experimentId, String experimentNo, String experimentName, String status, OffsetDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO experiment_record(
                  id, experiment_id, formula_columns, formula_rows, extra_tables, process_text, extra_processes, result_text,
                  created_at, updated_at)
                VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb), '[]'::jsonb, ?, '[]'::jsonb, ?, ?, ?)
                ON CONFLICT (experiment_id) DO NOTHING
                """, stableId("experiment-record-" + experimentNo), experimentId,
                "[{\"id\":\"material\",\"label\":\"原料名称\"},{\"id\":\"amount\",\"label\":\"用量\"},{\"id\":\"unit\",\"label\":\"单位\"}]",
                "[{\"material\":\"测试原料 A\",\"amount\":\"10\",\"unit\":\"g\"},{\"material\":\"测试原料 B\",\"amount\":\"2\",\"unit\":\"g\"}]",
                "按测试方案完成 " + experimentName + "；记录编号：" + experimentNo + "。",
                "completed".equals(status) ? "测试结果已归档，可用于接口展示与回归验证。" : "测试记录持续更新中。", createdAt, createdAt);
    }

    private void refreshExperimentCounts() {
        jdbcTemplate.update("""
                UPDATE project project_row SET experiment_count = counts.total
                FROM (
                  SELECT project_id, COUNT(*)::integer AS total
                  FROM experiment WHERE organization_id = ? GROUP BY project_id
                ) counts
                WHERE project_row.organization_id = ? AND project_row.id = counts.project_id
                """, ORGANIZATION_ID, ORGANIZATION_ID);
    }

    /** 为每个测试项目写入真实文档元数据和正文，供文档列表、预览、下载接口直接读取。 */
    private void createProjectDocuments() {
        List<DocumentProjectSeed> projects = jdbcTemplate.query("""
                SELECT id, project_no, name, owner_id, document_count, created_at
                FROM project WHERE organization_id = ?
                ORDER BY project_no
                """, (resultSet, rowNum) -> new DocumentProjectSeed(
                resultSet.getObject("id", UUID.class), resultSet.getString("project_no"), resultSet.getString("name"),
                resultSet.getObject("owner_id", UUID.class), resultSet.getInt("document_count"),
                resultSet.getObject("created_at", OffsetDateTime.class)), ORGANIZATION_ID);
        String[] categories = {"project_plan", "literature", "experiment_plan", "stage_report", "meeting_minutes", "other"};
        for (DocumentProjectSeed project : projects) {
            int targetCount = Math.max(project.documentCount(), 6);
            for (int index = 1; index <= targetCount; index++) {
                String category = categories[(index - 1) % categories.length];
                UUID documentId = stableId("project-document-" + project.projectNo() + "-" + index);
                OffsetDateTime createdAt = project.createdAt().plusDays(index);
                DevelopmentDocumentFixtureFactory.Fixture fixture = DevelopmentDocumentFixtureFactory.create(
                        category, project.projectNo(), project.name(), index, createdAt);
                jdbcTemplate.update("""
                        INSERT INTO project_document(
                          id, organization_id, project_id, category, name, version_label, file, mime_type, file_size,
                          uploaded_by_id, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET
                          category = EXCLUDED.category, name = EXCLUDED.name, version_label = EXCLUDED.version_label,
                          file = EXCLUDED.file, mime_type = EXCLUDED.mime_type, file_size = EXCLUDED.file_size,
                          updated_at = EXCLUDED.updated_at
                        """, documentId, ORGANIZATION_ID, project.id(), category, fixture.name(), "V" + ((index - 1) / 3 + 1) + ".0",
                        "database://project-documents/" + documentId, fixture.mimeType(), fixture.content().length,
                        project.ownerId(), createdAt, createdAt);
                jdbcTemplate.update("""
                        INSERT INTO project_document_content(document_id, content) VALUES (?, ?)
                        ON CONFLICT (document_id) DO UPDATE SET content = EXCLUDED.content
                        """, documentId, fixture.content());
            }
            jdbcTemplate.update("""
                    UPDATE project SET document_count = (SELECT COUNT(*) FROM project_document WHERE project_id = ?)
                    WHERE id = ?
                    """, project.id(), project.id());
        }
    }

    private List<ProjectSeed> legacyProjectSeeds() {
        return List.of(
                seed("PRJ-2026-PLA-001", "高性能PLA基可降解复合材料开发", "功能材料", "张伟", "开发兼具力学性能与可降解性的PLA基复合材料，完成配方筛选与性能验证。", "实验执行", 48, "active", "2026-03-01", "2026-12-31", "完成第三轮配方筛选", "2026-07-25", "current", 12, 11, 6),
                seed("PRJ-2026-SE-002", "固态电解质材料离子电导率优化研究", "新能源材料", "李娜", "优化固态电解质配方与制备工艺，提升材料离子传导稳定性。", "方案设计", 32, "active", "2026-04-01", "2026-11-30", "完成固态电解质配方初筛", "2026-07-30", "current", 8, 4, 4),
                seed("PRJ-2026-CO2-003", "CO₂加氢制备高附加值化学品工艺开发", "绿色化工", "王强", "构建CO₂加氢催化与工艺体系，完成催化剂活性和稳定性评价。", "检测分析", 67, "at_risk", "2026-02-15", "2026-10-31", "完成催化剂活性评价", "2026-07-22", "current", 15, 9, 9),
                seed("PRJ-2026-BIO-004", "生物基环氧树脂合成与性能研究", "环氧树脂", "陈思", "开发生物基环氧树脂合成与固化工艺，验证材料综合性能。", "实验执行", 52, "active", "2026-03-20", "2026-12-15", "完成力学性能测试", "2026-08-05", "current", 10, 6, 5),
                seed("PRJ-2026-PI-005", "耐高温聚酰亚胺薄膜制备工艺研究", "聚酰亚胺", "李娜", "开发长期耐温 300℃ 以上的聚酰亚胺薄膜制备工艺，完成连续制膜验证。", "实验执行", 58, "active", "2026-02-10", "2026-11-20", "完成工艺窗口验证", "2026-07-20", "current", 14, 4, 7),
                seed("PRJ-2026-EP-006", "低介电环氧树脂电子封装材料开发", "环氧树脂", "周浩", "开发兼顾低介电与高可靠性的环氧封装体系，完成关键配方及工艺验证。", "检测分析", 61, "active", "2026-01-18", "2026-10-18", "完成介电性能复测", "2026-07-26", "current", 11, 4, 8),
                seed("PRJ-2026-PI-007", "热塑性聚酰亚胺复合材料界面改性", "聚酰亚胺", "刘洋", "改善热塑性聚酰亚胺与增强相界面结合，提升复合材料加工与力学性能。", "实验执行", 43, "active", "2026-03-12", "2027-01-15", "完成偶联剂方案筛选", "2026-08-01", "current", 9, 4, 5),
                seed("PRJ-2026-BAT-008", "硅碳负极粘结剂体系性能优化", "新能源材料", "李娜", "优化硅碳负极水性粘结剂体系，改善电极循环稳定性与加工一致性。", "实验执行", 39, "active", "2026-04-08", "2026-12-28", "完成首轮循环性能评价", "2026-08-03", "current", 8, 4, 6),
                seed("PRJ-2026-EP-009", "阻燃环氧树脂灌封胶体系研发", "环氧树脂", "陈思", "构建低粘度无卤阻燃环氧灌封体系，平衡阻燃、导热和施工性能。", "方案设计", 28, "active", "2026-05-06", "2027-02-28", "完成阻燃剂组合初筛", "2026-08-08", "todo", 7, 4, 4),
                seed("PRJ-2026-MOF-010", "多孔吸附材料VOCs捕集性能研究", "功能材料", "王强", "开发面向VOCs捕集的多孔吸附材料，明确孔结构与吸附选择性的关联。", "检测分析", 55, "active", "2026-02-24", "2026-11-08", "完成动态吸附曲线测试", "2026-07-29", "current", 10, 5, 7),
                seed("PRJ-2026-PI-011", "光敏聚酰亚胺图形化性能评价", "聚酰亚胺", "赵敏", "评价光敏聚酰亚胺曝光、显影及热处理窗口，支撑精细图形制备。", "待开始", 8, "not_started", "2026-08-01", "2027-03-31", "完成基线配方准备", "2026-08-20", "todo", 4, 3, 2),
                seed("PRJ-2026-EP-012", "高韧性环氧结构胶配方开发", "环氧树脂", "张伟", "开发高韧性环氧结构胶，兼顾室温固化效率、粘接强度与耐湿热性能。", "待开始", 5, "not_started", "2026-08-15", "2027-04-20", "完成原料兼容性评估", "2026-09-05", "todo", 3, 3, 2),
                seed("PRJ-2026-SOD-013", "钠离子电池正极材料表面包覆研究", "新能源材料", "李娜", "优化钠离子电池正极材料包覆层组成及厚度，提升循环与倍率性能。", "待开始", 10, "not_started", "2026-07-25", "2027-03-15", "完成包覆工艺基线实验", "2026-08-18", "todo", 5, 3, 3),
                seed("PRJ-2026-CAT-014", "低温脱硝催化剂活性提升研究", "绿色化工", "王强", "开发低温区间高活性脱硝催化剂，降低水硫条件下活性衰减。", "待开始", 6, "not_started", "2026-08-10", "2027-02-10", "完成催化剂制备方案评审", "2026-09-01", "todo", 4, 3, 2),
                seed("PRJ-2026-PI-015", "聚酰亚胺气凝胶隔热材料开发", "聚酰亚胺", "刘洋", "开发轻质聚酰亚胺气凝胶隔热材料，优化孔结构、力学强度与耐热性能。", "待开始", 4, "not_started", "2026-09-01", "2027-06-30", "完成冻干工艺初步验证", "2026-09-25", "todo", 3, 3, 1),
                seed("PRJ-2026-MEM-016", "耐溶剂纳滤膜分离性能研究", "功能材料", "周浩", "开发耐溶剂纳滤膜，建立膜结构、溶剂稳定性与分离效率之间的关系。", "待开始", 7, "not_started", "2026-08-20", "2027-04-30", "完成膜材料候选清单", "2026-09-10", "todo", 4, 3, 2),
                seed("PRJ-2026-REC-017", "废旧复合材料化学回收工艺验证", "绿色化工", "陈思", "验证废旧复合材料低能耗化学回收路线，提高树脂和增强相回收利用率。", "待开始", 3, "not_started", "2026-09-15", "2027-05-31", "完成小试反应装置搭建", "2026-10-10", "todo", 2, 2, 1),
                seed("PRJ-2025-EP-018", "轨道交通用环氧防护涂层研究", "环氧树脂", "赵敏", "形成轨道交通用耐候环氧防护涂层配方及施工工艺。", "已归档", 100, "archived", "2025-05-01", "2026-06-20", "项目验收完成", "2026-06-20", "done", 18, 5, 12),
                seed("PRJ-2025-PI-019", "柔性基板用聚酰亚胺薄膜性能研究", "聚酰亚胺", "刘洋", "完成柔性基板用聚酰亚胺薄膜的热学、力学和介电性能评价。", "已归档", 100, "archived", "2025-04-10", "2026-06-15", "项目归档完成", "2026-06-15", "done", 16, 6, 10),
                seed("PRJ-2025-FC-020", "燃料电池双极板耐蚀涂层开发", "新能源材料", "李娜", "完成燃料电池双极板耐蚀导电涂层配方与寿命验证。", "已归档", 100, "archived", "2025-03-01", "2026-05-28", "结题验收完成", "2026-05-28", "done", 14, 6, 9));
    }

    private ProjectSeed seed(String projectNo, String name, String type, String owner, String description, String stage,
                             int progress, String status, String startDate, String endDate, String milestoneName,
                             String milestoneDate, String milestoneState, int documentCount, int experimentCount, int resourceCount) {
        return new ProjectSeed(stableId("project-" + projectNo), projectNo, name, type, description, stage, progress, status,
                ownerId(owner), atStartOfDay(startDate), atStartOfDay(endDate), List.of(description), milestoneName, milestoneDate,
                milestoneState, documentCount, experimentCount, resourceCount);
    }

    private UUID ownerId(String ownerName) {
        return switch (ownerName) {
            case "张伟" -> userId("zhangwei");
            case "李娜" -> userId("lina");
            case "王强" -> userId("wangqiang");
            case "陈思" -> userId("chensi");
            case "周浩" -> userId("zhouhao");
            case "刘洋" -> userId("liuyang");
            case "赵敏" -> userId("zhaomin");
            default -> throw new IllegalArgumentException("未配置测试项目负责人：" + ownerName);
        };
    }

    private UUID userId(String username) {
        return stableId("user-" + username);
    }

    private UUID stableId(String key) {
        return UUID.nameUUIDFromBytes(("materials-lab-dev-test-" + key).getBytes(StandardCharsets.UTF_8));
    }

    private OffsetDateTime atStartOfDay(String date) {
        return OffsetDateTime.parse(date + "T00:00:00+08:00");
    }

    private String experimentStatus(String projectStatus, int index) {
        if ("archived".equals(projectStatus)) return "completed";
        if ("not_started".equals(projectStatus)) return "not_started";
        if ("at_risk".equals(projectStatus)) return "in_progress";
        return switch (index % 3) {
            case 0 -> "completed";
            case 1 -> "in_progress";
            default -> "not_started";
        };
    }

    private String objectivesJson(List<String> objectives) {
        return "[" + objectives.stream().map(value -> "\"" + escapeJson(value) + "\"").reduce((left, right) -> left + "," + right).orElse("") + "]";
    }

    private String milestoneJson(String name, String date, String state) {
        return "[{\"name\":\"" + escapeJson(name) + "\",\"date\":\"" + escapeJson(date) + "\",\"state\":\"" + escapeJson(state) + "\"}]";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ProjectSeed(UUID id, String projectNo, String name, String projectType, String description, String stage,
                               int progress, String status, UUID ownerId, OffsetDateTime startDate, OffsetDateTime endDate,
                               List<String> objectives, String milestoneName, String milestoneDate, String milestoneState,
                               int documentCount, int experimentCount, int resourceCount) { }

    /** 写入项目文档测试数据所需的项目基础信息。 */
    private record DocumentProjectSeed(UUID id, String projectNo, String name, UUID ownerId, int documentCount,
                                       OffsetDateTime createdAt) { }
}
