package com.mdau.ushirika.module.messaging.dto;

import jakarta.validation.constraints.Size;

public record CloseThreadRequest(@Size(max = 500) String note) {}
