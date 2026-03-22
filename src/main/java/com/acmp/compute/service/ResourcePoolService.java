package com.acmp.compute.service;

import com.acmp.compute.dto.ResourcePoolCreateRequest;
import com.acmp.compute.dto.ResourcePoolResponse;
import com.acmp.compute.entity.ResourcePool;
import com.acmp.compute.exception.ResourceNotFoundException;
import com.acmp.compute.exception.ForbiddenException;
import com.acmp.compute.k8s.K8sResourceBuilder;
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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 逻辑资源池服务：创建时依次完成 Namespace → ResourceQuota → RBAC(SA/Role/RB) → Volcano Queue → 落库。
 * 所有用户通过平台代理操作，不 per-user 创建 K8s ServiceAccount。
 * 使用 fabric8 Builder API 构建 K8s 资源，无需模板引擎。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourcePoolService {

    private final ResourcePoolMapper resourcePoolMapper;
    private final PhysicalClusterMapper physicalClusterMapper;
    private final KubernetesClientManager clientManager;

    /**
     * 创建逻辑资源池（部门级）：严格按顺序完成以下步骤，保证 K8s 与 DB 一致。
     * 步骤：
     * 1) 校验物理集群存在
     * 2) 生成 namespace 与相关 K8s 资源名
     * 3) 创建 Namespace
     * 4) 创建 ResourceQuota（包含 maxPods 限制）
     * 5) 创建 ServiceAccount
     * 6) 创建 Role（部门级权限）
     * 7) 创建 RoleBinding
     * 8) 创建 Volcano Queue（集群级）
     * 9) 写入 DB
     */
    @Transactional(rollbackFor = Exception.class)
    public ResourcePoolResponse create(ResourcePoolCreateRequest request) {
        String physicalClusterId = request.getPhysicalClusterId();
        if (physicalClusterMapper.findById(physicalClusterId).isEmpty()) {
            throw new ResourceNotFoundException("物理集群不存在: " + physicalClusterId);
        }
        
        // 生成资源名
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        String namespace = "dept-" + request.getDepartmentCode() + "-" + shortId;
        String serviceAccountName = "sa-dept-" + request.getDepartmentCode();
        String roleName = "role-dept-" + request.getDepartmentCode();
        String roleBindingName = "rb-dept-" + request.getDepartmentCode();
        String quotaName = "quota-dept-" + request.getDepartmentCode();
        String volcanoQueueName = "queue-dept-" + request.getDepartmentCode();
        
        // 步骤 1：创建 Namespace
        clientManager.createNamespace(physicalClusterId, namespace);
        
        // 步骤 2：创建 ResourceQuota（包含 pods 限制）
        int maxPods = request.getMaxPods() != null ? request.getMaxPods() : 50;
        clientManager.createResourceQuota(
            physicalClusterId, namespace, quotaName,
            request.getGpuSlots(), request.getCpuCores(), request.getMemoryGiB(), maxPods
        );
        
        // 步骤 3：创建 ServiceAccount
        clientManager.createServiceAccount(physicalClusterId, namespace, serviceAccountName);
        
        // 步骤 4：创建 Role（部门专属权限）
        clientManager.createRole(physicalClusterId, namespace, roleName);
        
        // 步骤 5：创建 RoleBinding（绑定 SA 到 Role）
        clientManager.createRoleBinding(physicalClusterId, namespace, roleBindingName, roleName, serviceAccountName);
        
        // 步骤 6：创建 Volcano Queue（集群级 CRD）
        // 使用 Builder API 构建 Queue YAML（替代模板引擎）
        String queueYaml = K8sResourceBuilder.buildVolcanoQueue(
            volcanoQueueName,
            String.valueOf(request.getGpuSlots()),
            String.valueOf(request.getCpuCores()),
            String.valueOf(request.getMemoryGiB())
        );
        clientManager.applyClusterScopedYaml(physicalClusterId, queueYaml);
        // 步骤 7：将逻辑资源池记录落库
        String id = UUID.randomUUID().toString();
        ResourcePool pool = ResourcePool.builder()
                .id(id)
                .physicalClusterId(physicalClusterId)
                .name(request.getName())
                .departmentCode(request.getDepartmentCode())
                .departmentName(request.getDepartmentName())
                .namespace(namespace)
                .serviceAccountName(serviceAccountName)
                .gpuSlots(request.getGpuSlots())
                .cpuCores(request.getCpuCores())
                .memoryGiB(request.getMemoryGiB())
                .maxPods(maxPods)
                .volcanoQueueName(volcanoQueueName)
                .status("active")
                .build();
        resourcePoolMapper.insert(pool);
        
        log.info("✓ 已成功创建部门资源池 {} (namespace: {}, dept: {})",
                id, namespace, request.getDepartmentCode());
        
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
                .departmentCode(p.getDepartmentCode())
                .departmentName(p.getDepartmentName())
                .namespace(p.getNamespace())
                .serviceAccountName(p.getServiceAccountName())
                .gpuSlots(p.getGpuSlots())
                .cpuCores(p.getCpuCores())
                .memoryGiB(p.getMemoryGiB())
                .maxPods(p.getMaxPods())
                .volcanoQueueName(p.getVolcanoQueueName())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
