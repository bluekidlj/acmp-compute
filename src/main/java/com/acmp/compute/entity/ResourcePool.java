package com.acmp.compute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 逻辑资源池：一个 PhysicalCluster 下的 Namespace + ResourceQuota + Volcano Queue。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourcePool {
    private String id;
    private String physicalClusterId;
    private String name;
    /** K8s Namespace 名称，如 pool-org-a-123 */
    private String namespace;
    private Integer gpuSlots;
    private Integer cpuCores;
    private Integer memoryGiB;
    /** Volcano Queue 名称 */
    private String volcanoQueueName;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
