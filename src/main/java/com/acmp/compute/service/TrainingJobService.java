package com.acmp.compute.service;

import com.acmp.compute.dto.TrainingJobRequest;
import com.acmp.compute.entity.ResourcePool;
import com.acmp.compute.exception.ForbiddenException;
import com.acmp.compute.exception.ResourceNotFoundException;
import com.acmp.compute.k8s.K8sTemplateEngine;
import com.acmp.compute.k8s.KubernetesClientManager;
import com.acmp.compute.mapper.ResourcePoolMapper;
import com.acmp.compute.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 训练任务服务：提交 VolcanoJob 到逻辑资源池。
 * 校验用户拥有该 pool 权限（TRAINING_USER 或更高）后，渲染 volcano-job.yaml.ftl 并 apply。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingJobService {

    private final ResourcePoolMapper resourcePoolMapper;
    private final KubernetesClientManager clientManager;
    private final K8sTemplateEngine templateEngine;

    private UserPrincipal currentUser() {
        Object p = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(p instanceof UserPrincipal)) throw new ForbiddenException("未登录");
        return (UserPrincipal) p;
    }

    public String submit(String poolId, TrainingJobRequest request) {
        UserPrincipal user = currentUser();
        if (!user.canAccessPool(poolId)) throw new ForbiddenException("无权限在该资源池提交训练任务");
        ResourcePool pool = resourcePoolMapper.findById(poolId).orElseThrow(() -> new ResourceNotFoundException("资源池不存在: " + poolId));
        int minAvailable = request.getReplicas();
        Map<String, Object> data = new HashMap<>();
        data.put("jobName", request.getJobName());
        data.put("namespace", pool.getNamespace());
        data.put("queueName", pool.getVolcanoQueueName());
        data.put("minAvailable", minAvailable);
        data.put("image", request.getImage());
        data.put("replicas", request.getReplicas());
        data.put("gpuPerPod", request.getGpuPerPod());
        data.put("gpuMemPerPod", request.getGpuMemPerPod() != null ? request.getGpuMemPerPod() : 0);
        data.put("gpuCoresPerPod", request.getGpuCoresPerPod() != null ? request.getGpuCoresPerPod() : 0);
        data.put("command", request.getCommand() != null ? request.getCommand() : List.of());
        String yaml = templateEngine.render("volcano-job.yaml.ftl", data);
        clientManager.applyYamlInNamespace(pool.getPhysicalClusterId(), pool.getNamespace(), yaml);
        log.info("已提交 VolcanoJob {} @ pool {}", request.getJobName(), poolId);
        return request.getJobName();
    }
}
