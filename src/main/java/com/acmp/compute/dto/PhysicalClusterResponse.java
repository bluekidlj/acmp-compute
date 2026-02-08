package com.acmp.compute.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PhysicalClusterResponse {
    private String id;
    private String name;
    private String status;
    private Integer totalGpuSlots;
    private Instant createdAt;
    private Instant updatedAt;
}
