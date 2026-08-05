package com.materialslab.api.experiments.domain;

import java.util.UUID;

/** 实验参与人展示信息。 */
public record ExperimentParticipant(UUID userId, String displayName) { }
