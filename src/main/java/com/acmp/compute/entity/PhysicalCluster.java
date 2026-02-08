package com.acmp.compute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 物理集群实体：代表一个完整的 K8s 集群，通过 kubeconfig 连接。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhysicalCluster {
    private String id;
    private String name;
    /** AES 加密后的 kubeconfig 内容（Base64） */
    private String kubeconfigBase64Encrypted;
    /** active / degraded / offline */
    private String status;
    /** 可选缓存：集群总 GPU 槽数 */
    private Integer totalGpuSlots;
    private Instant createdAt;
    private Instant updatedAt;
}
