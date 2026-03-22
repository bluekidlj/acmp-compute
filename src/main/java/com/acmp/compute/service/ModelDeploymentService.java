package com.acmp.compute.service;

import com.acmp.compute.dto.ModelDeploymentResponse;
import com.acmp.compute.dto.VllmDeployRequest;
import com.acmp.compute.entity.ModelDeployment;
import com.acmp.compute.entity.ResourcePool;
import com.acmp.compute.exception.ForbiddenException;
import com.acmp.compute.exception.ResourceNotFoundException;
import com.acmp.compute.k8s.K8sResourceBuilder;
import com.acmp.compute.k8s.KubernetesClientManager;
import com.acmp.compute.mapper.ModelDeploymentMapper;
import com.acmp.compute.mapper.ResourcePoolMapper;
import com.acmp.compute.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * vLLM 模型服务部署：部署为最基础用户权限，凡拥有该 pool 即可部署。
 * 部署记录落库，状态可从 K8s 同步；模型与权重从本地获取（hostPath 挂载）。
 * 
 * 使用 fabric8 Builder API 构建 K8s 资源，获得类型安全和编译时检查的好处。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelDeploymentService {

    private final ModelDeploymentMapper modelDeploymentMapper;
    private final ResourcePoolMapper resourcePoolMapper;
    private final KubernetesClientManager clientManager;

    private UserPrincipal currentUser() {
        Object p = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(p instanceof UserPrincipal)) throw new ForbiddenException("未登录");
        return (UserPrincipal) p;
    }

    private void ensureCanAccessPool(String poolId) {
        UserPrincipal user = currentUser();
        if (!user.canAccessPool(poolId)) throw new ForbiddenException("无权限访问该资源池");
    }

    /**
     * 在逻辑资源池上部署 vLLM 模型服务。
     * 
     * 流程：
     * 1) 校验用户有权限访问该资源池
     * 2) 写部署记录到数据库（status=pending）
     * 3) 使用 fabric8 Builder API 构建 Deployment + Service（含 nodeSelector）
     * 4) 在 K8s 中创建资源
     * 5) 更新记录状态为 running，写入 serviceUrl
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelDeploymentResponse deploy(String poolId, VllmDeployRequest request) {
        ensureCanAccessPool(poolId);
        ResourcePool pool = resourcePoolMapper.findById(poolId)
                .orElseThrow(() -> new ResourceNotFoundException("资源池不存在: " + poolId));
        
        String deploymentName = "vllm-" + request.getName().toLowerCase().replaceAll("[^a-z0-9-]", "-");
        String serviceName = deploymentName + "-svc";
        if (deploymentName.length() > 50) deploymentName = deploymentName.substring(0, 50);
        if (serviceName.length() > 50) serviceName = serviceName.substring(0, 50);

        String id = UUID.randomUUID().toString();
        ModelDeployment record = ModelDeployment.builder()
                .id(id)
                .resourcePoolId(poolId)
                .name(request.getName())
                .modelName(request.getModelName())
                .modelSource(request.getModelSource())
                .modelIdOrPath(request.getModelIdOrPath())
                .vllmImage(request.getVllmImage())
                .gpuPerReplica(request.getGpuPerReplica())
                .gpumemMb(request.getGpumemMb())
                .gpucores(request.getGpucores())
                .replicas(request.getReplicas())
                .k8sDeploymentName(deploymentName)
                .k8sServiceName(serviceName)
                .status("pending")
                .createdBy(currentUser().getId())
                .build();
        modelDeploymentMapper.insert(record);

        try {
            // 使用 Builder API 构建 Deployment 和 Service YAML
            String yaml = K8sResourceBuilder.buildVllmDeploymentAndService(
                    deploymentName,
                    serviceName,
                    pool.getNamespace(),
                    request.getVllmImage(),
                    request.getModelIdOrPath() != null ? request.getModelIdOrPath() : "/models",
                    request.getGpuPerReplica(),
                    request.getGpumemMb(),
                    request.getGpucores(),
                    request.getReplicas(),
                    request.getHostModelPath()
            );
            
            // 在 K8s 中应用资源
            clientManager.createVllmDeploymentAndService(pool.getPhysicalClusterId(), pool.getNamespace(), yaml);
            
            String serviceUrl = "http://" + serviceName + "." + pool.getNamespace() + ".svc.cluster.local:8000";
            record.setStatus("running");
            record.setServiceUrl(serviceUrl);
            modelDeploymentMapper.update(record);
            
            log.info("✓ vLLM 模型 {} 已成功部署到资源池 {} (serviceUrl: {})", 
                    request.getName(), poolId, serviceUrl);
        } catch (Exception e) {
            log.error("✗ vLLM 部署失败: {}", e.getMessage(), e);
            record.setStatus("failed");
            modelDeploymentMapper.update(record);
            throw new RuntimeException("vLLM 部署失败: " + e.getMessage());
        }
        return toResponse(record, null);
    }

    public List<ModelDeploymentResponse> listByPool(String poolId) {
        ensureCanAccessPool(poolId);
        return modelDeploymentMapper.findByResourcePoolId(poolId).stream()
                .map(m -> toResponse(m, null))
                .collect(Collectors.toList());
    }

    public ModelDeploymentResponse getStatus(String poolId, String deploymentId) {
        ensureCanAccessPool(poolId);
        ModelDeployment record = modelDeploymentMapper.findById(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("部署记录不存在: " + deploymentId));
        if (!record.getResourcePoolId().equals(poolId)) throw new ForbiddenException("部署不属于该资源池");
        Integer readyReplicas = null;
        try {
            ResourcePool pool = resourcePoolMapper.findById(poolId).orElseThrow();
            readyReplicas = clientManager.getDeploymentReadyReplicas(pool.getPhysicalClusterId(), pool.getNamespace(), record.getK8sDeploymentName()).orElse(0);
        } catch (Exception ignored) {}
        return toResponse(record, readyReplicas);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String poolId, String deploymentId) {
        ensureCanAccessPool(poolId);
        ModelDeployment record = modelDeploymentMapper.findById(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("部署记录不存在: " + deploymentId));
        if (!record.getResourcePoolId().equals(poolId)) throw new ForbiddenException("部署不属于该资源池");
        ResourcePool pool = resourcePoolMapper.findById(poolId).orElseThrow();
        try {
            clientManager.deleteDeployment(pool.getPhysicalClusterId(), pool.getNamespace(), record.getK8sDeploymentName());
            clientManager.deleteService(pool.getPhysicalClusterId(), pool.getNamespace(), record.getK8sServiceName());
        } catch (Exception e) {
            log.warn("删除 K8s 资源失败: {}", e.getMessage());
        }
        modelDeploymentMapper.deleteById(deploymentId);
    }

    private ModelDeploymentResponse toResponse(ModelDeployment m, Integer readyReplicas) {
        return ModelDeploymentResponse.builder()
                .id(m.getId())
                .resourcePoolId(m.getResourcePoolId())
                .name(m.getName())
                .modelName(m.getModelName())
                .modelSource(m.getModelSource())
                .modelIdOrPath(m.getModelIdOrPath())
                .vllmImage(m.getVllmImage())
                .gpuPerReplica(m.getGpuPerReplica())
                .replicas(m.getReplicas())
                .k8sDeploymentName(m.getK8sDeploymentName())
                .k8sServiceName(m.getK8sServiceName())
                .status(m.getStatus())
                .serviceUrl(m.getServiceUrl())
                .readyReplicas(readyReplicas)
                .createdBy(m.getCreatedBy())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
