package com.materialslab.api.projects.mapper;

import com.materialslab.api.projects.domain.ProjectDocument;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.type.ByteArrayTypeHandler;

/** 项目文档元数据和正文的数据库访问层。 */
@Mapper
public interface ProjectDocumentMapper {
    /** 查询过滤后的项目文档。 */
    @SelectProvider(type = ProjectDocumentSqlProvider.class, method = "findByProject")
    List<ProjectDocument> findByProject(@Param("projectId") UUID projectId, @Param("category") String category,
                                        @Param("search") String search, @Param("fileType") String fileType,
                                        @Param("updatedRange") String updatedRange);

    /** 统计项目全部文档。 */
    @Select("SELECT COUNT(*) FROM project_document WHERE project_id = #{projectId}")
    long countByProject(@Param("projectId") UUID projectId);

    /** 统计当前过滤条件下的文档数。 */
    @SelectProvider(type = ProjectDocumentSqlProvider.class, method = "countByProject")
    long countFilteredByProject(@Param("projectId") UUID projectId, @Param("category") String category,
                                @Param("search") String search, @Param("fileType") String fileType,
                                @Param("updatedRange") String updatedRange);

    /** 统计每个文档分类的实际数量。 */
    @Select("SELECT category, COUNT(*) AS total FROM project_document WHERE project_id = #{projectId} GROUP BY category")
    List<CategoryCount> categoryCounts(@Param("projectId") UUID projectId);

    /** 查询指定项目中的文档元数据。 */
    @Select("""
            SELECT d.id, d.organization_id, d.project_id, d.category, d.name, d.version_label,
                   d.mime_type, d.file_size, d.uploaded_by_id, uploader.display_name AS uploaded_by_name,
                   d.created_at, d.updated_at
            FROM project_document d JOIN user_account uploader ON uploader.id = d.uploaded_by_id
            WHERE d.project_id = #{projectId} AND d.id = #{documentId}
            """)
    ProjectDocument findById(@Param("projectId") UUID projectId, @Param("documentId") UUID documentId);

    /** 读取文档真实二进制正文。 */
    @Select("SELECT content FROM project_document_content WHERE document_id = #{documentId}")
    @ConstructorArgs(@Arg(column = "content", javaType = byte[].class, typeHandler = ByteArrayTypeHandler.class))
    DocumentContentRow findContent(@Param("documentId") UUID documentId);

    /** 写入文档元数据。 */
    @Insert("""
            INSERT INTO project_document(
              id, organization_id, project_id, category, name, version_label, file, mime_type, file_size,
              uploaded_by_id, created_at, updated_at)
            VALUES (#{id}, #{organizationId}, #{projectId}, #{category}, #{name}, #{versionLabel}, #{file}, #{mimeType},
                    #{fileSize}, #{uploadedById}, #{createdAt}, #{updatedAt})
            """)
    int insert(DocumentWriteCommand command);

    /** 写入文档真实二进制正文。 */
    @Insert("INSERT INTO project_document_content(document_id, content) VALUES (#{documentId}, #{content})")
    int insertContent(@Param("documentId") UUID documentId, @Param("content") byte[] content);

    /** 同步项目页面展示的文档总数。 */
    @Update("""
            UPDATE project SET document_count = (SELECT COUNT(*) FROM project_document WHERE project_id = #{projectId}),
              updated_at = CURRENT_TIMESTAMP
            WHERE id = #{projectId}
            """)
    int refreshProjectDocumentCount(@Param("projectId") UUID projectId);

    /** 写入可审计的文档上传记录。 */
    @Insert("""
            INSERT INTO business_operation_log(
              id, organization_id, actor_id, domain, object_id, object_no, action_type, description, changes, created_at)
            VALUES (#{id}, #{organizationId}, #{actorId}, 'project', #{projectId}, #{projectNo}, 'document_uploaded',
                    #{description}, CAST(#{changes} AS jsonb), CURRENT_TIMESTAMP)
            """)
    int insertUploadOperationLog(@Param("id") UUID id, @Param("organizationId") UUID organizationId,
                                 @Param("actorId") UUID actorId, @Param("projectId") UUID projectId,
                                 @Param("projectNo") String projectNo, @Param("description") String description,
                                 @Param("changes") String changes);

    /** 分类数量的查询行。 */
    record CategoryCount(String category, long total) { }

    /** 文档正文查询行，避免 MyBatis 将 BYTEA 误映射为单个 byte。 */
    record DocumentContentRow(byte[] content) { }

    /** 文档新增写入参数。 */
    record DocumentWriteCommand(UUID id, UUID organizationId, UUID projectId, String category, String name,
                                String versionLabel, String file, String mimeType, long fileSize,
                                UUID uploadedById, OffsetDateTime createdAt, OffsetDateTime updatedAt) { }
}
