# ACMP 改造 - 快速参考指南

## 📚 核心改造本质

从 ❌ **基础多集群资源管理** → ✅ **企业级部门隔离、RBAC 自动生成、精细化管理**

---

## 🎯 三大核心改变

### 1️⃣ 部门隔离模型
```
之前：pool-uuid123 → namespace pool-uuid123
            ↓
之后：dept-finance-xyz123 → namespace dept-finance-xyz123 + SA + Role + RoleBinding
```
**关键点**：每个部门有独立的 K8s 资源权限

### 2️⃣ 自动 RBAC 生成
```
创建资源池 → 自动创建：
  ├─ Namespace
  ├─ ResourceQuota (GPU/CPU/内存/Pod数量限制)
  ├─ ServiceAccount
  ├─ Role (部门级权限)
  ├─ RoleBinding (SA → Role)
  └─ Volcano Queue
```
**关键点**：一个 API 调用完成 9 个 K8s 自动化操作

### 3️⃣ 强制 GPU 调度
```yaml
所有 Pod 强制注入：
nodeSelector:
  gpu-node: "true"
```
**关键点**：确保工作负载只在 GPU 节点运行

---

## 🔧 新增服务类（5 个）

| 类名 | 职责 | 关键方法 |
|------|------|---------|
| `AdminPhysicalClusterService` | 物理集群管理 | `registerCluster()` |
| `AdminResourcePoolService` | 凭证管理 | `issueCredential()` |
| `AdminController` | 管理员 API | 3 个 POST 接口 |
| `KubernetesClientManager` | RBAC 操作 | `createServiceAccount/Role/RoleBinding/extractCredentials()` |
| `ResourcePoolService` | 资源池创建 | `create()` (升级为 9 步自动化) |

---

## 📝 新增 API 接口（3 个）

```
管理员专用（需 PLATFORM_ADMIN 角色）：

1. POST /api/v1/admin/physical-clusters
   注册新的 K8s 集群
   
2. POST /api/v1/admin/resource-pools
   为部门创建资源池（自动生成 RBAC）
   
3. POST /api/v1/admin/resource-pools/{poolId}/issue-credential
   为部门员工发放 K8s 凭证
```

---

## 💾 数据库变化（4 项）

### 修改的表

| 表名 | 新增字段 | 用途 |
|------|---------|------|
| `physical_cluster` | description | 集群描述 |
| `resource_pool` | department_code, department_name, service_account_name, max_pods | 部门隔离 |

### 新增的表

| 表名 | 用途 |
|------|------|
| `resource_pool_credential` | 存储已发放的 K8s 凭证 |

### 新增索引（5 个）

便于查询优化（dept_code、cluster_id、pool_id 等）

---

## 🔐 权限模型

### 部门员工可做的操作（通过发放的凭证）

```yaml
- Pod: get, list, watch, create, delete (仅限所属 namespace)
- Deployment/StatefulSet: get, list, watch, create, update, patch, delete
- Job/VolcanoJob: 完全控制
- Service/ConfigMap/Secret: 完全控制
- PVC: 创建和删除
- Event: 只读（监控）
```

### 部门员工不能做的操作

```yaml
- 跨 namespace 访问 ❌
- 修改 ResourceQuota ❌
- 删除 Role/RoleBinding ❌
- 访问其他部门资源 ❌
```

管理员通过 **K8s RBAC** 自动强制执行

---

## 🚀 快速开始步骤

### Step 1: 启动应用
```bash
mvn spring-boot:run
```

### Step 2: 管理员登录获取 token
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.token')
```

### Step 3: 注册物理集群
```bash
curl -X POST http://localhost:8080/api/v1/admin/physical-clusters \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "prod-cluster-01",
    "description": "Production K8s Cluster",
    "kubeconfigBase64": "$(cat ~/.kube/config | base64 -w0)"
  }'
# 记下返回的 cluster_id
```

### Step 4: 为部门创建资源池
```bash
curl -X POST http://localhost:8080/api/v1/admin/resource-pools \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "physicalClusterId": "cluster_id_from_step_3",
    "name": "Finance Department Pool",
    "departmentCode": "finance",
    "departmentName": "财务部",
    "gpuSlots": 8,
    "cpuCores": 48,
    "memoryGiB": 256,
    "maxPods": 50
  }'
# 记下返回的 pool_id 和 namespace
```

### Step 5: 为部门员工发放凭证
```bash
curl -X POST http://localhost:8080/api/v1/admin/resource-pools/pool_id/issue-credential \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "zhangsan-finance",
    "expireDays": 30
  }'
# 得到 kubeconfig，转发给部门员工
```

### Step 6: 部门员工使用凭证
```bash
# 员工使用得到的 kubeconfig
export KUBECONFIG=/tmp/dept-finance-kubeconfig.yaml

# 只能看到自己的 namespace
kubectl get pods

# 提交任务（自动加上 nodeSelector）
kubectl apply -f training-job.yaml
```

---

## 🔍 验证改造是否成功

### 快速验证（3 分钟）

1. **检查编译**
   ```bash
   mvn clean compile
   ```
   ✅ 无错误

2. **检查数据库**
   ```bash
   # H2 console: http://localhost:8080/h2-console
   SELECT COUNT(*) FROM resource_pool_credential;
   ```
   ✅ 表存在

3. **测试 API**
   ```bash
   curl http://localhost:8080/api/v1/admin/physical-clusters \
     -H "Authorization: Bearer $TOKEN"
   ```
   ✅ 200 OK

### 完整验证（30 分钟）

见 `VERIFICATION-CHECKLIST.md`

---

## ⚠️ 常见问题速答

| 问题 | 答案 |
|------|------|
| nodeSelector 会限制我的 Pod 吗？ | ✅ 会。Pod 只能运行在有 `gpu-node=true` 标签的节点上 |
| 我能访问其他部门的资源池吗？ | ❌ 不能。RBAC 会阻止跨 namespace 访问 |
| 我的资源超过了配额怎么办？ | 📍 Pod 会 Pending，等待配额释放 |
| 凭证有效期多长？ | 📍 管理员指定（参数 expireDays），默认 30 天 |
| 我想重新生成凭证? | 📍 找管理员再调用一次 issue-credential API |

---

## 📚 重要文件定位

```
acmp-compute/
├── UPGRADE-SUMMARY.md                           ← 详细改造文档
├── VERIFICATION-CHECKLIST.md                    ← 测试清单
├── src/main/java/com/acmp/compute/
│   ├── service/
│   │   ├── AdminPhysicalClusterService.java     ← 集群管理
│   │   ├── AdminResourcePoolService.java        ← 凭证管理
│   │   └── ResourcePoolService.java             ← 资源池创建（已升级）
│   ├── controller/
│   │   └── AdminController.java                 ← 管理员接口
│   ├── k8s/
│   │   └── KubernetesClientManager.java         ← RBAC 操作（已增强）
│   ├── entity/
│   │   ├── PhysicalCluster.java                 ← 新增 description
│   │   ├── ResourcePool.java                    ← 新增部门字段
│   │   └── ResourcePoolCredential.java          ← 新增凭证表
│   └── dto/
│       ├── IssueCredentialRequest/Response.java ← 新增 DTO
│       └── ...
├── src/main/resources/
│   ├── schema-h2.sql                            ← 数据库 schema（已更新）
│   ├── k8s-templates/
│   │   ├── volcano-job.yaml.ftl                 ← 新增 nodeSelector
│   │   └── vllm-deployment.yaml.ftl             ← 新增 nodeSelector
│   └── mapper/
│       ├── ResourcePoolCredentialMapper.xml     ← 新增 mapper
│       └── ...
```

---

## 🎓 架构演进图

```
改造前（Simple）：
┌─────────────────────────────────┐
│  API（业务用户）                 │
├─────────────────────────────────┤
│  Single Service Layer           │
├─────────────────────────────────┤
│  One Namespace = One Pool       │ ← 简单但缺乏隔离
├─────────────────────────────────┤
│  K8s Cluster (1个或多个)          │
└─────────────────────────────────┘

改造后（Enterprise）：
┌──────────────────────────────────────────────────────────┐
│  Admin APIs (集群注册、资源池创建、凭证发放)  User APIs   │
├──────────────────────────────────────────────────────────┤
│  AdminPhysicalClusterService                             │
│  AdminResourcePoolService                                │
│  ResourcePoolService (RBAC自动化)                        │
│  KubernetesClientManager (增强RBAC操作)                 │
├──────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────┐  ┌─────────────────┐  │
│  │ Finance Dept               │  │ HR Dept        │  │
│  │ ├─ Namespace              │  │ ├─ Namespace   │  │
│  │ ├─ ResourceQuota          │  │ ├─ ResourceQuota│  │
│  │ ├─ SA + Role + RoleBinding│  │ ├─ SA + Role + RB│  │
│  │ └─ max nodeSelector       │  │ └─ nodeSelector │  │
│  └─────────────────────────────┘  └─────────────────┘  │
├──────────────────────────────────────────────────────────┤
│  K8s Cluster 1 | K8s Cluster 2 | K8s Cluster 3 ...     │
└──────────────────────────────────────────────────────────┘
```

---

## ✅ 改造验收标准

| 标准 | 状态 |
|------|------|
| 支持多物理集群 | ✅ 完成 |
| 部门级 Namespace 隔离 | ✅ 完成 |
| RBAC 自动生成 | ✅ 完成 |
| nodeSelector 强制注入 | ✅ 完成 |
| 凭证管理 | ✅ 完成 |
| ResourceQuota 精细控制 | ✅ 完成 |
| 权限校验 | ✅ 完成 |
| 向后兼容 | ✅ 完成 |

---

## 📞 支持与反馈

- 详细文档：见 `UPGRADE-SUMMARY.md`
- 验证步骤：见 `VERIFICATION-CHECKLIST.md`
- 代码位置：见上方"重要文件定位"
- 问题反馈：检查日志，查看 service 层的 log.info() 输出

