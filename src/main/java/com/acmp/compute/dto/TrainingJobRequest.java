package com.acmp.compute.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class TrainingJobRequest {
    @NotBlank
    private String jobName;
    @NotBlank
    private String image;
    @NotNull @Min(1)
    private Integer replicas;
    @NotNull @Min(0)
    private Integer gpuPerPod;
    private Integer gpuMemPerPod;
    private Integer gpuCoresPerPod;
    private List<String> command;
}
