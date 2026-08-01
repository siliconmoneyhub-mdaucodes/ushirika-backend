package com.mdau.ushirika.module.messaging.dto;

import java.util.UUID;

/** programId null = general inquiry thread to admin staff; non-null = thread with that program's coordinators. */
public record StartThreadRequest(UUID programId) {}
