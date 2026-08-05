package com.materialslab.api.projects.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 工作台查询结果的数据传输对象集合。 */
public final class DashboardRows {
    private DashboardRows() { }

    /** 项目或实验的状态统计。 */
    public record Metrics(long total, long active, long archived, long atRisk, long inProgress, long completed) { }

    /** 研发类型分布。 */
    public record TypeDistribution(String name, long projectCount, long experimentCount) { }

    /** 按日期和类型聚合的实验数量。 */
    public record TrendEntry(LocalDate date, String typeName, long experimentCount) { }

    /** 工作台项目卡片所需字段。 */
    public record ActiveProject(UUID id, String projectNo, String name, String projectTypeCode, String ownerName,
                                String objectives, String milestones, int progressPercent, OffsetDateTime plannedStartDate,
                                OffsetDateTime plannedEndDate, boolean followed) { }

    /** 总览趋势筛选的项目选项。 */
    public record ProjectOption(UUID id, String projectNo, String name) { }
}
