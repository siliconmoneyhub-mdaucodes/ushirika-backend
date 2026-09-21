package com.mdau.ushirika.module.messaging.dto;

import com.mdau.ushirika.module.messaging.enums.ThreadPriority;

import java.util.UUID;

/**
 * programId null = general inquiry thread to admin staff; non-null = thread with that program's coordinators.
 * priority null = NORMAL. (Only applied when the thread is newly created; an existing thread keeps its priority.)
 */
public record StartThreadRequest(UUID programId, ThreadPriority priority) {}
