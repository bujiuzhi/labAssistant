package com.materialslab.api.experiments.domain;

import tools.jackson.databind.JsonNode;
import java.util.List;

/** 面向前端电子实验记录本的记录内容。 */
public record ExperimentRecordResponse(
        JsonNode formulaColumns,
        JsonNode formulaRows,
        JsonNode extraTables,
        String processText,
        JsonNode extraProcesses,
        List<ExperimentAttachment> processImages,
        String resultText,
        List<ExperimentAttachment> resultFiles) { }
