package com.settl.backend.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank(message = "Group name is required")
        @Size(min = 2, max = 100, message = "Group name must be between 2 and 100 characters")
        String name,

        @NotBlank(message = "Default currency is required")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "Default currency must be a 3-letter ISO-4217 code (e.g. USD, EUR, INR)")
        String defaultCurrency
) {
}
