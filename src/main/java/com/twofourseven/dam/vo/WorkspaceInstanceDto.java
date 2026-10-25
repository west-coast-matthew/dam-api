package com.twofourseven.dam.vo;

import com.twofourseven.dam.entities.core.Workspace;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request and response body for /workspaces. No relations are carried.
 * {@code id} and {@code createdAt} are set by the server and ignored on input.
 */
public record WorkspaceInstanceDto(
        UUID id,
        @NotBlank(message = "Workspace name is required")
        @Size(max = Workspace.NAME_MAX_LENGTH) String name,
        @Size(max = Workspace.DESCRIPTION_MAX_LENGTH) String description,
        LocalDateTime createdAt) {
}
