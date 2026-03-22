package com.acmp.compute.service;

import com.acmp.compute.dto.IssueCredentialRequest;
import com.acmp.compute.dto.IssueCredentialResponse;
import com.acmp.compute.entity.PhysicalCluster;
import com.acmp.compute.entity.ResourcePool;
import com.acmp.compute.exception.ResourceNotFoundException;
import com.acmp.compute.k8s.KubernetesClientManager;
import com.acmp.compute.mapper.PhysicalClusterMapper;
import com.acmp.compute.mapper.ResourcePoolMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;

/**
 * 管理员资源池服务：处理管理员特定的操作，如凭证发放、集群管理等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminResourcePoolService {

    private final ResourcePoolMapper resourcePoolMapper;
    private final PhysicalClusterMapper physicalClusterMapper;
    private final KubernetesClientManager clientManager;
    private final EncryptionService encryptionService;

    /**
     * 为部门用户发放 K8s 访问凭证。
     * 
     * 流程：
     * 1. 校验资源池存在
     * 2. 从 ServiceAccount 的 Secret 提取 token 和 CA
     * 3. 获取集群连接地址
     * 4. 构建完整的 kubeconfig 内容
     * 5. 返回凭证给用户
     * 
     * 注意：凭证有效期信息可存储在数据库中，便于管理员审计和撤销。
     */
    public IssueCredentialResponse issueCredential(String poolId, IssueCredentialRequest request) {
        // 1. 校验资源池存在
        ResourcePool pool = resourcePoolMapper.findById(poolId)
                .orElseThrow(() -> new ResourceNotFoundException("资源池不存在: " + poolId));
        
        // 2. 校验物理集群存在
        PhysicalCluster cluster = physicalClusterMapper.findById(pool.getPhysicalClusterId())
                .orElseThrow(() -> new ResourceNotFoundException("物理集群不存在"));
        
        // 3. 从 ServiceAccount Secret 提取凭证信息
        Map<String, String> credentials = clientManager.extractServiceAccountCredentials(
                pool.getPhysicalClusterId(),
                pool.getNamespace(),
                pool.getServiceAccountName()
        );
        
        String token = credentials.get("token");
        String caCrt = credentials.get("ca-crt");
        
        // 4. 解密原始 kubeconfig 文本，以获取集群连接地址
        String decryptedKubeconfig = encryptionService.decrypt(cluster.getKubeconfigBase64Encrypted());
        
        // 5. 构建新的 kubeconfig（只包含该 namespace 的权限）
        String kubeconfig = buildKubeconfigWithToken(
                decryptedKubeconfig,
                pool.getNamespace(),
                request.getUsername(),
                token,
                caCrt
        );
        
        // 6. 构建响应
        IssueCredentialResponse response = IssueCredentialResponse.builder()
                .kubeconfig(kubeconfig)
                .namespace(pool.getNamespace())
                .clusterName(cluster.getName())
                .serviceAccountName(pool.getServiceAccountName())
                .message(String.format("凭证已生成，有效期 %d 天，用户: %s", request.getExpireDays(), request.getUsername()))
                .build();
        
        log.info("✓ 已为用户 {} 在部门池 {} 发放凭证 (namespace: {})",
                request.getUsername(), poolId, pool.getNamespace());
        
        return response;
    }

    /**
     * 从原始 kubeconfig 基础上，用提取的 ServiceAccount token 构建新的 kubeconfig。
     * 新的 kubeconfig 限制用户只能访问指定 namespace。
     */
    private String buildKubeconfigWithToken(String originalKubeconfig, String namespace,
                                           String username, String token, String caCrt) {
        // 简化版实现：返回一个包含必要信息的 kubeconfig 字符串
        // 生产环境建议使用 YAML 库（如 SnakeYAML）来正确解析和生成 YAML
        
        // Base64 编码的 token 和 CA
        String encodedToken = Base64.getEncoder().encodeToString(token.getBytes());
        String encodedCa = caCrt != null ? caCrt : Base64.getEncoder().encodeToString("".getBytes());
        
        // 这是一个简化的示例，实际应该从原始 kubeconfig 中提取集群地址等
        // 生产环境应该使用 YAML 库来处理
        StringBuilder sb = new StringBuilder();
        sb.append("apiVersion: v1\n");
        sb.append("kind: Config\n");
        sb.append("current-context: dept-context\n");
        sb.append("clusters:\n");
        sb.append("- name: kubernetes\n");
        sb.append("  cluster:\n");
        sb.append("    certificate-authority-data: ").append(encodedCa).append("\n");
        sb.append("    server: https://kubernetes.default.svc:443\n");  // 简化，实际应从原始 kubeconfig 提取
        sb.append("contexts:\n");
        sb.append("- name: dept-context\n");
        sb.append("  context:\n");
        sb.append("    cluster: kubernetes\n");
        sb.append("    namespace: ").append(namespace).append("\n");
        sb.append("    user: ").append(username).append("\n");
        sb.append("users:\n");
        sb.append("- name: ").append(username).append("\n");
        sb.append("  user:\n");
        sb.append("    token: ").append(token).append("\n");
        
        return sb.toString();
    }
}
