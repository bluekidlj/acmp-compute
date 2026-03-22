package com.acmp.compute.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 发放部门级凭证请求：为指定用户生成具有访问特定 K8s Namespace 权限的凭证。
 */
@Data
public class IssueCredentialRequest {
    @NotBlank
    private String username;
    @NotNull @Min(1)
    private Integer expireDays = 30;  // 默认 30 天
}
