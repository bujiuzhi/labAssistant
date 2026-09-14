package com.materialslab.api.projects.mapper;

import java.util.Map;

/** 生成项目文档筛选 SQL，所有动态值均使用 MyBatis 参数绑定。 */
public class ProjectDocumentSqlProvider {
    /** 生成文档元数据查询。 */
    public String findByProject(Map<String, Object> parameters) {
        return "SELECT d.id, d.organization_id, d.project_id, d.category, d.name, d.version_label, d.file, "
                + "d.mime_type, d.file_size, d.uploaded_by_id, uploader.display_name AS uploaded_by_name, "
                + "d.created_at, d.updated_at FROM project_document d "
                + "JOIN user_account uploader ON uploader.id = d.uploaded_by_id "
                + where(parameters) + " ORDER BY d.updated_at DESC, d.created_at DESC";
    }

    /** 生成文档数量查询。 */
    public String countByProject(Map<String, Object> parameters) {
        return "SELECT COUNT(*) FROM project_document d " + where(parameters);
    }

    private String where(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("WHERE d.project_id = #{projectId}");
        if (present(parameters, "category")) sql.append(" AND d.category = #{category}");
        if (present(parameters, "search")) {
            sql.append(" AND (d.name ILIKE CONCAT('%', #{search}, '%') OR d.version_label ILIKE CONCAT('%', #{search}, '%') ")
                    .append("OR EXISTS (SELECT 1 FROM user_account account WHERE account.id = d.uploaded_by_id ")
                    .append("AND account.display_name ILIKE CONCAT('%', #{search}, '%'))) ");
        }
        if (present(parameters, "fileType")) {
            sql.append(" AND lower(split_part(d.name, '.', array_length(string_to_array(d.name, '.'), 1))) IN ");
            String fileType = String.valueOf(parameters.get("fileType"));
            sql.append(switch (fileType) {
                case "word" -> "('doc', 'docx', 'odt', 'rtf')";
                case "pdf" -> "('pdf')";
                case "excel" -> "('xls', 'xlsx', 'csv', 'ods')";
                case "powerpoint" -> "('ppt', 'pptx', 'odp')";
                case "image" -> "('png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp')";
                case "text" -> "('txt', 'md', 'log')";
                default -> "('')";
            });
        }
        if (present(parameters, "updatedRange")) {
            sql.append(" AND d.updated_at >= CURRENT_TIMESTAMP - INTERVAL '")
                    .append("week".equals(parameters.get("updatedRange")) ? "7 days" : "30 days")
                    .append("'");
        }
        return sql.toString();
    }

    private boolean present(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        return value != null && !String.valueOf(value).isBlank();
    }
}
