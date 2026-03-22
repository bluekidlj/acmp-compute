package com.acmp.compute.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * 创建部门逻辑资源池请求：指定物理集群、部门信息、资源容量。
 */
@Data
public class ResourcePoolCreateRequest {
    @NotBlank
    private String physicalClusterId;
    @NotBlank
    private String name;
    @NotBlank
    @Pattern(regexp = "^[a-z0-9_-]+$", message = "departmentCode 只能包含小写字母、数字、下划线和连字符")
    private String departmentCode;
    @NotBlank
    private String departmentName;
    @NotNull @Min(1)
    private Integer gpuSlots;
    @NotNull @Min(1)
    private Integer cpuCores;
    @NotNull @Min(1)
    private Integer memoryGiB;
    private Integer maxPods = 50;  // 默认值为 50
}
