package com.acmp.compute.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ResourcePoolResponse {
    private String id;
    private String physicalClusterId;
    private String name;
    private String departmentCode;
    private String departmentName;
    private String namespace;
    private String serviceAccountName;
    private Integer gpuSlots;
    private Integer cpuCores;
    private Integer memoryGiB;
    private Integer maxPods;
    private String volcanoQueueName;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
