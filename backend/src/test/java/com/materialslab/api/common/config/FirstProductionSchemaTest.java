package com.materialslab.api.common.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 首次正式发布只打包单一空库 V1 基线，后续变更从 V2 开始。 */
class FirstProductionSchemaTest {

    @Test
    void packagesSingleCompleteV1Baseline() throws IOException {
        var resource = getClass().getResourceAsStream("/db/migration/V1__materials_lab_schema.sql");
        assertNotNull(resource, "必须打包首版 V1 迁移");
        String schema = new String(resource.readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(schema.contains("CREATE TABLE project_document_content"));
        assertTrue(schema.contains("CREATE TABLE experiment_attachment_content"));
        assertTrue(schema.contains("CONSTRAINT uk_user_account_username UNIQUE (username)"));
        assertTrue(schema.contains("is_platform_admin BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(schema.contains("session_version BIGINT NOT NULL DEFAULT 0"));
        assertTrue(schema.contains("首版起新上传正文保存于 RustFS"));
        assertTrue(schema.contains("COMMENT ON TABLE organization"));
        Path migrationDirectory = Path.of("src", "main", "resources", "db", "migration");
        assertTrue(Files.notExists(migrationDirectory.resolve("V2__rustfs_object_storage.sql")));
        assertTrue(Files.notExists(migrationDirectory.resolve("V2__project_document_content.sql")));
        assertTrue(Files.notExists(migrationDirectory.resolve("V3__experiment_attachments_and_global_username.sql")));
    }
}
