package com.mdau.ushirika.module.messaging.dto;

import com.mdau.ushirika.module.messaging.enums.ThreadPriority;
import jakarta.validation.constraints.NotNull;

public record SetPriorityRequest(@NotNull ThreadPriority priority) {}
