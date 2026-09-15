package com.materialslab.api.common.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 首版发布前将全部已确认结构收敛为唯一 V1 空库基线。 */
class FirstProductionSchemaTest {

    @Test
    void providesOneCompleteFirstReleaseSchema() throws IOException {
        var resource = getClass().getResourceAsStream("/db/migration/V1__materials_lab_schema.sql");
        assertNotNull(resource, "必须打包首版 V1 迁移");
        String schema = new String(resource.readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(schema.contains("CREATE TABLE organization"));
        assertTrue(schema.contains("is_platform_admin BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(schema.contains("CREATE TABLE registration_invitation"));
        assertTrue(schema.contains("CREATE TABLE project_document_content"));
        assertTrue(schema.contains("CREATE TABLE experiment_attachment_content"));
        assertTrue(schema.contains("deleted_by_id UUID REFERENCES user_account(id)"));
        assertTrue(schema.contains("fk_user_account_platform_membership"));
        Path migrationDirectory = Path.of("src", "main", "resources", "db", "migration");
        assertTrue(Files.notExists(migrationDirectory.resolve("V2__platform_tenant_boundary.sql")));
        assertTrue(Files.notExists(migrationDirectory.resolve("V3__logical_user_deletion.sql")));
        assertTrue(Files.notExists(migrationDirectory.resolve("V4__platform_account_membership.sql")));
    }
}
