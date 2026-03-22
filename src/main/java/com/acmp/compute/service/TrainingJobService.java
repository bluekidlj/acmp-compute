package com.acmp.compute.service;

import com.acmp.compute.dto.TrainingJobRequest;
import com.acmp.compute.entity.ResourcePool;
import com.acmp.compute.exception.ForbiddenException;
import com.acmp.compute.exception.ResourceNotFoundException;
import com.acmp.compute.k8s.K8sResourceBuilder;
import com.acmp.compute.k8s.KubernetesClientManager;
import com.acmp.compute.mapper.ResourcePoolMapper;
import com.acmp.compute.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 训练任务服务：提交 VolcanoJob 到逻辑资源池。
 * 使用 fabric8 Builder API 构建 VolcanoJob，获得类型安全和编译时检查的好处。
 * 
 * 校验用户拥有该资源池权限后，自动构建 VolcanoJob YAML（含 nodeSelector 和资源配额）并提交。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingJobService {

    private final ResourcePoolMapper resourcePoolMapper;
    private final KubernetesClientManager clientManager;

    private UserPrincipal currentUser() {
        Object p = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(p instanceof UserPrincipal)) throw new ForbiddenException("未登录");
        return (UserPrincipal) p;
    }

    public String submit(String poolId, TrainingJobRequest request) {
        UserPrincipal user = currentUser();
        if (!user.canAccessPool(poolId)) throw new ForbiddenException("无权限在该资源池提交训练任务");
        
        ResourcePool pool = resourcePoolMapper.findById(poolId)
                .orElseThrow(() -> new ResourceNotFoundException("资源池不存在: " + poolId));
        
        try {
            // 使用 Builder API 构建 VolcanoJob YAML
            String yaml = K8sResourceBuilder.buildVolcanoJob(
                    request.getJobName(),
                    pool.getNamespace(),
                    pool.getVolcanoQueueName(),
                    request.getReplicas(),
                    request.getImage(),
                    request.getGpuPerPod(),
                    request.getGpuMemPerPod(),
                    request.getGpuCoresPerPod(),
                    request.getCommand()
            );
            
            // 在 K8s 中应用资源
            clientManager.applyYamlInNamespace(pool.getPhysicalClusterId(), pool.getNamespace(), yaml);
            
            log.info("✓ VolcanoJob {} 已成功提交到资源池 {} (namespace: {}, queue: {})", 
                    request.getJobName(), poolId, pool.getNamespace(), pool.getVolcanoQueueName());
            
            return request.getJobName();
        } catch (Exception e) {
            log.error("✗ 训练任务提交失败: {}", e.getMessage(), e);
            throw new RuntimeException("训练任务提交失败: " + e.getMessage());
        }
    }
}
