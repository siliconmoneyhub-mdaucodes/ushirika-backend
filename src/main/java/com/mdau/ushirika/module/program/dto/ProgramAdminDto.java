package com.mdau.ushirika.module.program.dto;

import com.mdau.ushirika.module.program.entity.ProgramAdminAssignment;

import java.util.UUID;

public record ProgramAdminDto(
        UUID assignmentId,
        UUID userId,
        String fullName,
        String email
) {
    public static ProgramAdminDto from(ProgramAdminAssignment a) {
        return new ProgramAdminDto(
                a.getId(),
                a.getUser().getId(),
                a.getUser().getFullName(),
                a.getUser().getEmail()
        );
    }
}
