package com.mdau.ushirika.module.auth.dto;

import java.time.Instant;

public record ActivationTicketDto(String ticket, Instant expiresAt) {}
