package com.pitchmap.publicdata.api;

import com.pitchmap.publicdata.domain.SyncJobType;
import jakarta.validation.constraints.NotNull;

public record SyncJobLaunchRequest(@NotNull SyncJobType jobType) {}
