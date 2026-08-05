package com.materialslab.api.experiments.domain;

import java.util.UUID;

/** 电子实验记录附件展示信息。 */
public record ExperimentAttachment(UUID id, String name, String url, String size) { }
