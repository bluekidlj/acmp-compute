package com.acmp.compute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 资源池凭证：为部门用户发放的 kubeconfig 凭证，用于直接访问 K8s。
 * 包含 ServiceAccount token、namespace、集群名称等信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourcePoolCredential {
    private String id;
    private String resourcePoolId;
    /** 凭证所属用户名 */
    private String username;
    /** 完整的 kubeconfig 内容（含 token）*/
    private String kubeconfig;
    /** 过期时间 */
    private Instant expireAt;
    private Instant createdAt;
    private Instant updatedAt;
}
