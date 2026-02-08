package com.acmp.compute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 组织实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization {
    private String id;
    private String name;
    private Instant createdAt;
    private Instant updatedAt;
}
