package com.acmp.compute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 用户实体。resourcePoolIds 通过 user_resource_pool 表关联查询。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private String id;
    private String username;
    private String passwordHash;
    /** PLATFORM_ADMIN / ORG_ADMIN / TRAINING_USER / INFERENCE_USER */
    private String role;
    private String organizationId;
    private Instant createdAt;
    private Instant updatedAt;
    /** 可访问的资源池 ID 列表（关联查询，不持久化在 user 表） */
    private List<String> resourcePoolIds;
}
