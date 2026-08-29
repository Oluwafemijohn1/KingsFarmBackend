package com.kingsfarm.kingsfarmbackend.relief.dto;

import jakarta.validation.constraints.NotNull;

public record CreateReliefGrantRequest(
        @NotNull Long onLeaveUserId,
        @NotNull Long granteeUserId,
        String reason
) {
}
