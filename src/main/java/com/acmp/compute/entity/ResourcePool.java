package com.acmp.compute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 逻辑资源池：一个 PhysicalCluster 下的 Namespace + ResourceQuota + RBAC + Volcano Queue。
 * 与部门（业务单位）一一对应，提供完整的隔离与权限管理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourcePool {
    private String id;
    private String physicalClusterId;
    private String name;
    /** 部门代码，用于生成 namespace 与 RBAC 资源名（e.g., dept-finance-abc123） */
    private String departmentCode;
    /** 部门名称 */
    private String departmentName;
    /** K8s Namespace 名称，如 dept-finance-abc123 */
    private String namespace;
    /** ServiceAccount 名称，如 sa-dept-finance */
    private String serviceAccountName;
    private Integer gpuSlots;
    private Integer cpuCores;
    private Integer memoryGiB;
    /** Pod 数量限制 */
    private Integer maxPods;
    /** Volcano Queue 名称 */
    private String volcanoQueueName;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
