# 请求处理与 K8s 交互流程

## 1. 整体流程（从前端到 K8s）

```mermaid
sequenceDiagram
  participant Client
  participant Gateway as JWT Filter
  participant Ctrl as Controller
  participant Svc as Service
  participant K8sMgr as K8s Client Manager
  participant K8s as Kubernetes API

  Client->>Gateway: POST .../model-deployments (Body + Bearer)
  Gateway->>Gateway: 校验 JWT，设置 UserPrincipal
  Gateway->>Ctrl: 进入 Controller
  Ctrl->>Svc: deploy(poolId, VllmDeployRequest)
  Svc->>Svc: 校验用户拥有 poolId（基础权限）
  Svc->>Svc: 写入 model_deployment 记录 status=pending
  Svc->>K8sMgr: getClient(physicalClusterId)
  K8sMgr->>K8sMgr: 从缓存或新建 KubernetesClient
  Svc->>Svc: 渲染 vllm-deployment.yaml.ftl
  Svc->>K8sMgr: createVllmDeploymentAndService(namespace, yaml)
  K8sMgr->>K8s: create Deployment + Service in namespace
  K8s-->>K8sMgr: 创建成功
  Svc->>Svc: 更新记录 status=running, service_url
  Svc-->>Ctrl: ModelDeploymentResponse
  Ctrl-->>Client: 201 + 部署记录与 serviceUrl
```

## 2. 训练任务提交流程（VolcanoJob）

1. 客户端携带 JWT 调用 `POST /api/v1/resource-pools/{poolId}/training-jobs`。
2. JWT Filter 解析 token，将 UserPrincipal（含 role、resourcePoolIds）放入 SecurityContext。
3. Controller 调用 `TrainingJobService.submit(poolId, request)`。
4. Service 校验 `user.canAccessPool(poolId)`，加载 ResourcePool，渲染 `volcano-job.yaml.ftl`，调用 `KubernetesClientManager.applyYamlInNamespace` 在对应 namespace 下创建 VolcanoJob。
5. 返回 201 与 jobName。

## 3. 示例报文（vLLM 部署）

- **请求**：见 [EXAMPLE-REQUEST.md](EXAMPLE-REQUEST.md) 第 5 节。
- **响应**：返回部署记录 id、status、serviceUrl 等；后续可通过 `GET .../model-deployments/{id}` 查询状态（含 K8s readyReplicas）。
