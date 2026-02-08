package com.acmp.compute.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * vLLM 模型服务部署请求。
 * 模型与权重从本地获取，modelIdOrPath 为宿主机挂载到容器内的路径（如 /models/qwen3）。
 */
@Data
public class VllmDeployRequest {
    @NotBlank
    private String name;
    private String modelName;
    /** with_weights / without_weights */
    @NotBlank
    private String modelSource;
    /** 本地路径：容器内模型路径（或挂载点），如 /models/qwen3 */
    private String modelIdOrPath;
    @NotBlank
    private String vllmImage;
    @NotNull @Min(1)
    private Integer gpuPerReplica;
    private Integer gpumemMb;
    private Integer gpucores;
    @NotNull @Min(1)
    private Integer replicas;
    /** 宿主机权重目录（用于挂载，可选），如 /data/models/Qwen3 */
    private String hostModelPath;
}
