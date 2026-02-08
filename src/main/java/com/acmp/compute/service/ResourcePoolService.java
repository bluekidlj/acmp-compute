package com.acmp.compute.service;

import com.acmp.compute.dto.ResourcePoolCreateRequest;
import com.acmp.compute.dto.ResourcePoolResponse;
import com.acmp.compute.entity.ResourcePool;
import com.acmp.compute.exception.ResourceNotFoundException;
import com.acmp.compute.exception.ForbiddenException;
import com.acmp.compute.k8s.K8sTemplateEngine;
import com.acmp.compute.k8s.KubernetesClientManager;
import com.acmp.compute.mapper.PhysicalClusterMapper;
import com.acmp.compute.mapper.ResourcePoolMapper;
import com.acmp.compute.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 逻辑资源池服务：创建时依次完成 Namespace → ResourceQuota → Volcano Queue → 落库。
 * 所有用户通过平台代理操作，不 per-user 创建 K8s ServiceAccount。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourcePoolService {

    private final ResourcePoolMapper resourcePoolMapper;
    private final PhysicalClusterMapper physicalClusterMapper;
    private final KubernetesClientManager clientManager;
    private final K8sTemplateEngine templateEngine;

    /**
     * 创建逻辑资源池：严格按顺序完成以下步骤，保证 K8s 与 DB 一致。
     * 1) 生成 namespace 与 queue 名 2) 创建 Namespace 3) 创建 ResourceQuota 4) 创建 Volcano Queue 5) 写入 DB。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResourcePoolResponse create(ResourcePoolCreateRequest request) {
        String physicalClusterId = request.getPhysicalClusterId();
        if (physicalClusterMapper.findById(physicalClusterId).isEmpty()) {
            throw new ResourceNotFoundException("物理集群不存在: " + physicalClusterId);
        }
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        String namespace = "pool-" + shortId;
        String volcanoQueueName = "queue-" + shortId;

        // 步骤 1：在物理集群下创建 Namespace，作为该逻辑池的隔离边界
        clientManager.createNamespace(physicalClusterId, namespace);

        // 步骤 2：创建 ResourceQuota，限制该 namespace 内 GPU/CPU/Memory 总量（与池容量一致）
        String quotaYaml = templateEngine.render("resource-quota.yaml.ftl", Map.of(
                "namespace", namespace,
                "gpuSlots", request.getGpuSlots(),
                "cpuCores", request.getCpuCores(),
                "memoryGiB", request.getMemoryGiB()
        ));
        clientManager.applyYamlInNamespace(physicalClusterId, namespace, quotaYaml);

        // 步骤 3：创建 Volcano Queue（集群级 CRD），用于批调度与队列配额
        String queueYaml = templateEngine.render("volcano-queue.yaml.ftl", Map.of(
                "queueName", volcanoQueueName,
                "gpuSlots", request.getGpuSlots(),
                "cpuCores", request.getCpuCores(),
                "memoryGiB", request.getMemoryGiB()
        ));
        clientManager.applyClusterScopedYaml(physicalClusterId, queueYaml);

        // 步骤 4：将逻辑资源池记录落库，供后续部署与任务使用
        String id = UUID.randomUUID().toString();
        ResourcePool pool = ResourcePool.builder()
                .id(id)
                .physicalClusterId(physicalClusterId)
                .name(request.getName())
                .namespace(namespace)
                .gpuSlots(request.getGpuSlots())
                .cpuCores(request.getCpuCores())
                .memoryGiB(request.getMemoryGiB())
                .volcanoQueueName(volcanoQueueName)
                .status("active")
                .build();
        resourcePoolMapper.insert(pool);
        return toResponse(resourcePoolMapper.findById(id).orElseThrow());
    }

    public List<ResourcePoolResponse> list() {
        return resourcePoolMapper.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** 获取资源池详情；非 PLATFORM_ADMIN/ORG_ADMIN 时校验当前用户是否拥有该 pool 权限 */
    public ResourcePoolResponse getById(String id) {
        ResourcePool pool = resourcePoolMapper.findById(id).orElseThrow(() -> new ResourceNotFoundException("资源池不存在: " + id));
        Object p = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (p instanceof UserPrincipal) {
            UserPrincipal user = (UserPrincipal) p;
            if (!user.canAccessPool(id)) throw new ForbiddenException("无权限访问该资源池");
        }
        return toResponse(pool);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResourcePoolResponse patchCapacity(String id, Integer gpuSlots, Integer cpuCores, Integer memoryGiB) {
        ResourcePool pool = resourcePoolMapper.findById(id).orElseThrow(() -> new ResourceNotFoundException("资源池不存在: " + id));
        if (gpuSlots != null) pool.setGpuSlots(gpuSlots);
        if (cpuCores != null) pool.setCpuCores(cpuCores);
        if (memoryGiB != null) pool.setMemoryGiB(memoryGiB);
        resourcePoolMapper.update(pool);
        return toResponse(resourcePoolMapper.findById(id).orElseThrow());
    }

    private ResourcePoolResponse toResponse(ResourcePool p) {
        return ResourcePoolResponse.builder()
                .id(p.getId())
                .physicalClusterId(p.getPhysicalClusterId())
                .name(p.getName())
                .namespace(p.getNamespace())
                .gpuSlots(p.getGpuSlots())
                .cpuCores(p.getCpuCores())
                .memoryGiB(p.getMemoryGiB())
                .volcanoQueueName(p.getVolcanoQueueName())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
