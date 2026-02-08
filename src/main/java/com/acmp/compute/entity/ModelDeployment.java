package com.acmp.compute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * vLLM 模型服务部署记录。
 * 模型与权重从本地获取（宿主机目录或 PVC），暂不考虑 OBS/SWR。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelDeployment {
    private String id;
    private String resourcePoolId;
    /** 部署名称，如 qwen3-svc-001 */
    private String name;
    private String modelName;
    /** with_weights：镜像内或挂载路径有权重；without_weights：仅运行时镜像，须挂载权重路径 */
    private String modelSource;
    /** 本地路径：宿主机挂载到容器内的路径，如 /models/qwen3 */
    private String modelIdOrPath;
    private String vllmImage;
    private Integer gpuPerReplica;
    private Integer gpumemMb;
    private Integer gpucores;
    private Integer replicas;
    private String k8sDeploymentName;
    private String k8sServiceName;
    /** pending / running / failed / stopped */
    private String status;
    /** 服务访问地址，如 http://svc-name.namespace.svc.cluster.local:8000 */
    private String serviceUrl;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
