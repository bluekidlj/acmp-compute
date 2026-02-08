package com.acmp.compute.k8s;

import com.acmp.compute.entity.PhysicalCluster;
import com.acmp.compute.mapper.PhysicalClusterMapper;
import com.acmp.compute.service.EncryptionService;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Kubernetes 客户端管理器：按物理集群 ID 缓存 KubernetesClient，
 * 使用平台高权限 ServiceAccount 代理操作 K8s，不 per-user 创建 ServiceAccount。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KubernetesClientManager {

    private final PhysicalClusterMapper physicalClusterMapper;
    private final EncryptionService encryptionService;

    /** 集群 ID -> 已创建的 KubernetesClient 缓存，避免重复解析 kubeconfig */
    private final Map<String, KubernetesClient> clientCache = new ConcurrentHashMap<>();

    /**
     * 获取指定物理集群的 Kubernetes 客户端。若缓存不存在则从库中取 kubeconfig 解密后创建并缓存。
     */
    public KubernetesClient getClient(String physicalClusterId) {
        return clientCache.computeIfAbsent(physicalClusterId, this::createAndCacheClient);
    }

    private KubernetesClient createAndCacheClient(String physicalClusterId) {
        PhysicalCluster cluster = physicalClusterMapper.findById(physicalClusterId)
                .orElseThrow(() -> new IllegalArgumentException("集群不存在: " + physicalClusterId));
        String decrypted = encryptionService.decrypt(cluster.getKubeconfigBase64Encrypted());
        Config config = Config.fromKubeconfig(decrypted);
        return new KubernetesClientBuilder().withConfig(config).build();
    }

    /** 移除并关闭指定集群的客户端（例如集群删除或 kubeconfig 更新后调用） */
    public void closeClient(String physicalClusterId) {
        KubernetesClient client = clientCache.remove(physicalClusterId);
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("关闭 K8s 客户端异常: {}", e.getMessage());
            }
        }
    }

    /** 在指定集群下创建 Namespace */
    public void createNamespace(String physicalClusterId, String namespaceName) {
        KubernetesClient client = getClient(physicalClusterId);
        Namespace ns = new NamespaceBuilder()
                .withNewMetadata().withName(namespaceName).endMetadata()
                .build();
        client.namespaces().resource(ns).createOrReplace();
        log.info("已创建 Namespace: {} @ cluster {}", namespaceName, physicalClusterId);
    }

    /**
     * 在指定 namespace 下创建 ResourceQuota，限制 GPU/CPU/Memory。
     * 与逻辑资源池容量一致，便于池化隔离。
     */
    public void createResourceQuota(String physicalClusterId, String namespace, String quotaName,
                                    int gpuSlots, int cpuCores, int memoryGiB) {
        KubernetesClient client = getClient(physicalClusterId);
        ResourceQuota quota = new ResourceQuotaBuilder()
                .withNewMetadata().withName(quotaName).withNamespace(namespace).endMetadata()
                .withNewSpec()
                .addToHard("nvidia.com/gpu", Quantity.parse(String.valueOf(gpuSlots)))
                .addToHard("cpu", Quantity.parse(String.valueOf(cpuCores)))
                .addToHard("memory", Quantity.parse(memoryGiB + "Gi"))
                .addToHard("pods", Quantity.parse("200"))
                .endSpec()
                .build();
        client.resourceQuotas().inNamespace(namespace).resource(quota).createOrReplace();
        log.info("已创建 ResourceQuota: {} @ namespace {}", quotaName, namespace);
    }

    /**
     * 使用渲染后的 YAML 在指定 namespace 创建或替换资源（如 Deployment、Service）。
     * 用于 vLLM Deployment、VolcanoJob 等。
     */
    @SuppressWarnings("rawtypes")
    public void applyYamlInNamespace(String physicalClusterId, String namespace, String yaml) {
        KubernetesClient client = getClient(physicalClusterId);
        try {
            client.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)))
                    .inNamespace(namespace)
                    .createOrReplace();
        } catch (Exception e) {
            log.error("应用 YAML 失败: {}", e.getMessage());
            throw new RuntimeException("应用 YAML 失败", e);
        }
    }

    /**
     * 创建 vLLM Deployment 与 Service（通过 YAML 一次性 apply）。
     * 具体结构由 K8sTemplateEngine + vllm-deployment.yaml.ftl 生成。
     */
    public void createVllmDeploymentAndService(String physicalClusterId, String namespace, String yaml) {
        applyYamlInNamespace(physicalClusterId, namespace, yaml);
    }

    /** 删除指定 namespace 下的 Deployment */
    public void deleteDeployment(String physicalClusterId, String namespace, String deploymentName) {
        KubernetesClient client = getClient(physicalClusterId);
        client.apps().deployments().inNamespace(namespace).withName(deploymentName).delete();
        log.info("已删除 Deployment: {} @ {}", deploymentName, namespace);
    }

    /** 删除指定 namespace 下的 Service */
    public void deleteService(String physicalClusterId, String namespace, String serviceName) {
        KubernetesClient client = getClient(physicalClusterId);
        client.services().inNamespace(namespace).withName(serviceName).delete();
        log.info("已删除 Service: {} @ {}", serviceName, namespace);
    }

    /**
     * 获取 Deployment 的 ready 副本数，用于状态同步。
     */
    public Optional<Integer> getDeploymentReadyReplicas(String physicalClusterId, String namespace, String deploymentName) {
        KubernetesClient client = getClient(physicalClusterId);
        Deployment deployment = client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
        if (deployment == null || deployment.getStatus() == null) return Optional.of(0);
        Integer ready = deployment.getStatus().getReadyReplicas();
        return Optional.ofNullable(ready == null ? 0 : ready);
    }

    /**
     * 应用集群级别资源 YAML（如 Volcano Queue）。Queue 无 metadata.namespace，创建为集群级资源。
     */
    public void applyClusterScopedYaml(String physicalClusterId, String yaml) {
        KubernetesClient client = getClient(physicalClusterId);
        try {
            client.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))).createOrReplace();
        } catch (Exception e) {
            log.error("应用集群级 YAML 失败: {}", e.getMessage());
            throw new RuntimeException("应用集群级 YAML 失败", e);
        }
    }

    /** 验证 kubeconfig 是否可用：尝试创建客户端并执行一次 list namespaces。 */
    public boolean validateKubeconfig(String kubeconfigPlain) {
        try {
            Config config = Config.fromKubeconfig(kubeconfigPlain);
            try (KubernetesClient client = new KubernetesClientBuilder().withConfig(config).build()) {
                client.namespaces().list();
                return true;
            }
        } catch (Exception e) {
            log.warn("kubeconfig 校验失败: {}", e.getMessage());
            return false;
        }
    }
}
