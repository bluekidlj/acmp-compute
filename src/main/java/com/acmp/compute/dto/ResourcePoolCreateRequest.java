package com.acmp.compute.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ResourcePoolCreateRequest {
    @NotBlank
    private String physicalClusterId;
    @NotBlank
    private String name;
    @NotNull @Min(1)
    private Integer gpuSlots;
    @NotNull @Min(1)
    private Integer cpuCores;
    @NotNull @Min(1)
    private Integer memoryGiB;
}
