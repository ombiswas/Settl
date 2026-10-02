package com.settl.backend.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGroupRequest(
        @NotBlank(message = "Group name is required")
        @Size(max = 150, message = "Group name cannot exceed 150 characters")
        String name,

        @Size(min = 3, max = 3, message = "Default currency must be a 3-letter ISO-4217 code")
        String defaultCurrency
) {
}
