package com.helpdesk.modules.identity.dto;

import com.helpdesk.modules.identity.entity.ChannelCode;
import com.helpdesk.modules.identity.entity.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminUserCreateRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Size(max = 30) String phone,
            @NotEmpty Set<RoleCode> roles,
            Boolean available
    ) {
    }

    public record AdminUserUpdateRequest(
            @Size(max = 150) String fullName,
            @Size(max = 30) String phone,
            Boolean active,
            Boolean available,
            Set<RoleCode> roles
    ) {
    }

    public record AdminTeamCreateRequest(
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull Long supervisorId
    ) {
    }

    public record AdminTeamUpdateRequest(
            @Size(max = 100) String name,
            Long supervisorId,
            Boolean active
    ) {
    }

    public record AdminCategoryCreateRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 100) String name
    ) {
    }

    public record AdminCategoryUpdateRequest(
            @Size(max = 100) String name,
            Boolean active
    ) {
    }

    public record ChannelAccountDto(
            Long id,
            Long channelId,
            ChannelCode channelCode,
            String accountCode,
            String displayName,
            String configMetadata,
            boolean active,
            Instant createdAt
    ) {
    }

    public record ChannelWithAccountsDto(
            Long id,
            ChannelCode code,
            String name,
            boolean active,
            List<ChannelAccountDto> accounts
    ) {
    }

    public record ChannelAccountCreateRequest(
            @NotNull ChannelCode channelCode,
            @NotBlank @Size(max = 100) String accountCode,
            @NotBlank @Size(max = 150) String displayName,
            String configMetadata
    ) {
    }

    public record ChannelAccountUpdateRequest(
            @Size(max = 150) String displayName,
            String configMetadata,
            Boolean active
    ) {
    }
}
