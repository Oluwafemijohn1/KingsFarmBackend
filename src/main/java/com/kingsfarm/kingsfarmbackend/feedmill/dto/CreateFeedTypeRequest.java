package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateFeedTypeRequest(@NotBlank String name) {
}
