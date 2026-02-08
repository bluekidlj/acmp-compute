package com.acmp.compute.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 注册物理集群请求：name + kubeconfig Base64 */
@Data
public class PhysicalClusterRegisterRequest {
    @NotBlank
    private String name;
    /** 原始 kubeconfig 内容（或 Base64 编码后的字符串，由前端约定） */
    @NotBlank
    private String kubeconfigBase64;
}
