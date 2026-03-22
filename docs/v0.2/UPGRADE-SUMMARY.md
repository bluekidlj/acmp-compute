# ACMP 计算平台 - 精细化资源管理改造总结

## 改造概述

本次改造将 ACMP（AI 计算平台）从简单的多集群资源管理升级为**企业级的部门隔离、RBAC 自动生成、精细化资源管理平台**。

---

## 核心改造内容

### 1. 数据模型增强

#### 1.1 PhysicalCluster（物理集群）
- **新增字段**：`description`（集群描述）
- **用途**：支持管理员为多个物理集群添加标注，便于识别和管理

#### 1.2 ResourcePool（逻辑资源池）- **核心改造**
- **新增字段**：
  - `departmentCode`：部门代码（用于生成 K8s 资源名称）
  - `departmentName`：部门名称
  - `serviceAccountName`：对应的 K8s ServiceAccount 名称
  - `maxPods`：Pod 数量限制（默认 50）
  
- **用途**：将资源池与特定部门绑定，实现部门级隔离

#### 1.3 新增 ResourcePoolCredential（资源池凭证表）
- **字段**：id, resourcePoolId, username, kubeconfig, expireAt, createdAt, updatedAt
- **用途**：存储为部门用户发放的 K8s 访问凭证，支持凭证管理和审计

### 2. API 接口设计

#### 2.1 管理员接口（新增 /api/v1/admin）

**POST /api/v1/admin/physical-clusters** - 注册新物理集群
```json
{
  "name": "beijing-cluster-01",
  "description": "北京机房主集群",
  "kubeconfigBase64": "base64编码的complete kubeconfig"
}
```
后台操作：
- Base64 解码 kubeconfig
- 验证 kubeconfig 有效性
- AES 加密存储
- 初始化客户端缓存

**POST /api/v1/admin/resource-pools** - 创建部门逻辑资源池
```json
{
  "physicalClusterId": "uuid-of-cluster",
  "name": "财务部资源池",
  "departmentCode": "finance",
  "departmentName": "财务部",
  "gpuSlots": 8,
  "cpuCores": 48,
  "memoryGiB": 256,
  "maxPods": 50
}
```
后台自动完成的操作：
1. 校验物理集群存在
2. 生成 namespace：`dept-{departmentCode}-{随机8位}`
3. 创建 Namespace
4. 创建 ResourceQuota（包含 GPU、CPU、内存、Pod 限制）
5. 创建 ServiceAccount：`sa-dept-{departmentCode}`
6. 创建 Role：`role-dept-{departmentCode}`（部门级权限）
7. 创建 RoleBinding：将 Role 绑定到 ServiceAccount
8. 创建 Volcano Queue（集群级资源）
9. 写入数据库记录

**POST /api/v1/admin/resource-pools/{poolId}/issue-credential** - 发放用户凭证
```json
{
  "username": "zhangsan-finance",
  "expireDays": 30
}
```
响应：
```json
{
  "kubeconfig": "...完整的kubeconfig内容...",
  "namespace": "dept-finance-abc123",
  "clusterName": "beijing-cluster-01",
  "serviceAccountName": "sa-dept-finance",
  "message": "凭证已生成，有效期30天，用户: zhangsan-finance"
}
```

#### 2.2 业务用户接口（保留现有）
- POST /api/v1/resource-pools/{poolId}/inference-services - 部署推理服务
- POST /api/v1/resource-pools/{poolId}/training-jobs - 提交训练任务

### 3. K8s RBAC 自动生成

#### 3.1 自动创建的 Role 权限范围

部门用户通过自动创建的 Role 获得以下权限（仅限其所属 namespace）：

```yaml
# Pod 相关
- pods, pods/log, pods/exec: get, list, watch, create, delete

# Deployment 和 StatefulSet
- deployments, statefulsets: get, list, watch, create, update, patch, delete

# Job
- jobs: get, list, watch, create, update, patch, delete

# VolcanoJob（分布式训练）
- volcanojobs: get, list, watch, create, update, patch, delete

# Service 和配置
- services, configmaps, secrets: get, list, watch, create, update, patch, delete

# PVC（数据持久化）
- persistentvolumeclaims: get, list, watch, create, delete

# 监控
- events: get, list, watch
```

所有权限**仅限于资源池对应的 namespace**，实现完整隔离。

### 4. 强制 nodeSelector 注入

所有 Pod 定义模板中强制添加：
```yaml
nodeSelector:
  gpu-node: "true"
```

**确保场景**：
- vLLM Deployment 部署
- VolcanoJob 训练任务
- 所有业务工作负载

**作用**：强制所有 Pod 调度到带有 `gpu-node=true` 标签的节点。

### 5. 服务层改造

#### 5.1 KubernetesClientManager - RBAC 操作增强

新增方法：
- `createServiceAccount(physicalClusterId, namespace, saName)`：创建 SA
- `createRole(physicalClusterId, namespace, roleName)`：创建 Role（含完整权限）
- `createRoleBinding(physicalClusterId, namespace, rbName, roleName, saName)`：创建 RoleBinding
- `extractServiceAccountCredentials(physicalClusterId, namespace, saName)`：提取 SA token 和 CA
- `createResourceQuota(..., maxPods)`：创建配额（新增 Pod 数量限制）

#### 5.2 ResourcePoolService - 完整 RBAC 流程

`create()` 方法重构，自动化完成 9 个步骤：
1. 校验物理集群
2. 生成资源名称
3. 创建 Namespace
4. 创建 ResourceQuota
5. 创建 ServiceAccount
6. 创建 Role
7. 创建 RoleBinding
8. 创建 Volcano Queue
9. 落库

#### 5.3 AdminResourcePoolService - 凭证发放

新增服务，处理凭证发放逻辑：
- 从 ServiceAccount Secret 提取 token 和 CA
- 构建完整的 kubeconfig
- 返回给用户

#### 5.4 AdminPhysicalClusterService - 集群注册

新增服务，处理物理集群注册：
- Base64 解码 kubeconfig
- 验证 kubeconfig 有效性
- AES 加密存储
- 初始化客户端缓存

### 6. 数据库结构升级

#### 6.1 更新的表

**physical_cluster**：
```sql
- 新增: description VARCHAR(512)
```

**resource_pool**：
```sql
- 新增: department_code VARCHAR(64) NOT NULL
- 新增: department_name VARCHAR(255)
- 新增: service_account_name VARCHAR(255)
- 新增: max_pods INT DEFAULT 50
- 修改: namespace 添加 UNIQUE 约束
```

#### 6.2 新增的表

**resource_pool_credential**：
```sql
- id, resourcePoolId, username, kubeconfig, expireAt, createdAt, updatedAt
- 用于存储已发放的凭证记录
```

#### 6.3 新增索引

- `idx_resource_pool_dept(department_code)`
- `idx_resource_pool_cluster(physical_cluster_id)`
- `idx_credential_pool(resource_pool_id)`

---

## 工作流程示例

### 场景：财务部申请资源池+凭证

#### 第 1 步：管理员注册物理集群（仅需一次）

```bash
curl -X POST http://localhost:8080/api/v1/admin/physical-clusters \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "beijing-cluster-01",
    "description": "北京机房主集群",
    "kubeconfigBase64": "LS0tLS1CRUdJTi..."
  }'
```

响应：
```json
{
  "id": "cluster-uuid-001",
  "name": "beijing-cluster-01",
  "description": "北京机房主集群",
  "status": "active"
}
```

#### 第 2 步：管理员为财务部创建资源池

```bash
curl -X POST http://localhost:8080/api/v1/admin/resource-pools \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "physicalClusterId": "cluster-uuid-001",
    "name": "财务部资源池",
    "departmentCode": "finance",
    "departmentName": "财务部",
    "gpuSlots": 8,
    "cpuCores": 48,
    "memoryGiB": 256,
    "maxPods": 50
  }'
```

后台自动操作：
- 在 K8s 中创建：namespace `dept-finance-xyz123`
- 创建 ResourceQuota：gpu=8, cpu=48, memory=256Gi, pods=50
- 创建 ServiceAccount：`sa-dept-finance`
- 创建 Role：`role-dept-finance`（含部门权限）
- 创建 RoleBinding：绑定 SA 到 Role
- 创建 Volcano Queue：`queue-dept-finance`

响应：
```json
{
  "id": "pool-uuid-001",
  "physicalClusterId": "cluster-uuid-001",
  "name": "财务部资源池",
  "departmentCode": "finance",
  "departmentName": "财务部",
  "namespace": "dept-finance-xyz123",
  "serviceAccountName": "sa-dept-finance",
  "gpuSlots": 8,
  "cpuCores": 48,
  "memoryGiB": 256,
  "maxPods": 50,
  "volcanoQueueName": "queue-dept-finance",
  "status": "active"
}
```

#### 第 3 步：管理员为部门用户发放凭证

```bash
curl -X POST http://localhost:8080/api/v1/admin/resource-pools/pool-uuid-001/issue-credential \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "zhangsan-finance",
    "expireDays": 30
  }'
```

后台操作：
- 从 SA Secret 提取 token 和 CA
- 生成 kubeconfig（含 token、CA、服务器地址）

响应：
```json
{
  "kubeconfig": "apiVersion: v1\nkind: Config\n...",
  "namespace": "dept-finance-xyz123",
  "clusterName": "beijing-cluster-01",
  "serviceAccountName": "sa-dept-finance",
  "message": "凭证已生成，有效期30天，用户: zhangsan-finance"
}
```

#### 第 4 步：财务部员工使用凭证提交任务

```bash
# 设置 kubeconfig
export KUBECONFIG=$(mktemp)
echo "<凭证-kubeconfig内容>" > $KUBECONFIG

# 提交训练任务
curl -X POST http://localhost:8080/api/v1/resource-pools/pool-uuid-001/training-jobs \
  -H "Authorization: Bearer <user-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "jobName": "finance-train-001",
    "image": "nvcr.io/nvidia/pytorch:22.12-py3",
    "replicas": 4,
    "gpuPerPod": 2,
    "command": ["python", "train.py"]
  }'
```

后台操作：
- 校验用户权限（该资源池是否关联用户）
- 渲染 VolcanoJob YAML（**强制注入 nodeSelector: gpu-node=true**）
- 在 `dept-finance-xyz123` namespace 中创建 Job
- 资源受到 ResourceQuota 限制（8 GPU、48 CPU、256Gi 内存）
- RaleBinding 确保用户只能在该 namespace 中操作

---

## 验收检查点

### 1. **多物理集群支持**
- ✅ 可多次注册不同的物理集群
- ✅ 系统缓存并管理多个 KubernetesClient
- ✅ 每个资源池可关联不同的物理集群

### 2. **部门级资源池隔离**
- ✅ 每个资源池对应唯一的 namespace
- ✅ 部门代码自动生成资源名称
- ✅ ResourceQuota 严格限制资源使用

### 3. **RBAC 隔离**
- ✅ 自动创建 ServiceAccount、Role、RoleBinding
- ✅ 部门用户只能访问所属 namespace
- ✅ 通过 Role 限制具体操作权限

### 4. **强制 nodeSelector**
- ✅ 所有 Pod（vLLM、VolcanoJob）都带有 `gpu-node=true`
- ✅ 确保工作负载调度到 GPU 节点

### 5. **凭证管理**
- ✅ 可为任意用户生成访问凭证
- ✅ 凭证包含完整的 kubeconfig（token、CA、服务器地址）
- ✅ 支持凭证过期时间管理

### 6. **权限校验**
- ✅ 管理员接口需要 PLATFORM_ADMIN 角色
- ✅ 业务用户提交任务时校验资源池访问权限
- ✅ 超过 ResourceQuota 后无法创建新 Pod

---

## 未来扩展方向

1. **自动节点发现**：自动发现并标记 GPU 节点
2. **凭证生命周期管理**：自动过期、撤销、续期
3. **成本计费**：按部门/用户统计资源使用量和成本
4. **多租户隔离**：支持 NetworkPolicy、PodSecurityPolicy
5. **监控告警**：集成 Prometheus、Alertmanager
6. **自动扩缩容**：根据负载自动扩展 K8s 节点

---

## 技术栈不变

- Java 11 + Spring Boot 2.7
- fabric8 Kubernetes Client 6.13.x
- H2 + MyBatis
- Spring Security + JWT
- Freemarker 模板引擎

---

## 关键代码位置

| 功能 | 文件位置 |
|------|---------|
| RBAC 操作 | `src/main/java/com/acmp/compute/k8s/KubernetesClientManager.java` |
| 资源池创建 | `src/main/java/com/acmp/compute/service/ResourcePoolService.java` |
| 凭证发放 | `src/main/java/com/acmp/compute/service/AdminResourcePoolService.java` |
| 集群注册 | `src/main/java/com/acmp/compute/service/AdminPhysicalClusterService.java` |
| 管理员接口 | `src/main/java/com/acmp/compute/controller/AdminController.java` |
| nodeSelector | `src/main/resources/k8s-templates/volcano-job.yaml.ftl`, `vllm-deployment.yaml.ftl` |
| 数据库 | `src/main/resources/schema-h2.sql` |

