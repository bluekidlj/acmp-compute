package com.acmp.compute.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 发放部门级凭证响应：包含完整的 kubeconfig、namespace、集群名称等信息。
 */
@Data
@Builder
public class IssueCredentialResponse {
    /** 完整的 kubeconfig 内容（含 token、CA、服务器地址等） */
    private String kubeconfig;
    /** 该凭证可访问的 Namespace */
    private String namespace;
    /** 所属物理集群名称 */
    private String clusterName;
    /** ServiceAccount 名称 */
    private String serviceAccountName;
    /** 凭证有效期提示 */
    private String message;
}
