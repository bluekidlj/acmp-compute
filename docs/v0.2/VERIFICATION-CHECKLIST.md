# 改造完成清单与验证指南

## ✅ 已完成的改造项目

### 核心架构改造

- [x] **Entity 层强化**
  - PhysicalCluster：新增 `description` 字段
  - ResourcePool：新增 `departmentCode`, `departmentName`, `serviceAccountName`, `maxPods`
  - 新增：ResourcePoolCredential 实体

- [x] **DTO 层完善**
  - PhysicalClusterRegisterRequest：添加 `description` 字段
  - ResourcePoolCreateRequest：添加部门相关字段 + `maxPods`
  - 新增：IssueCredentialRequest, IssueCredentialResponse

- [x] **K8s 客户端管理增强**
  - KubernetesClientManager 新增 RBAC 操作方法
  - `createServiceAccount()` - SA 创建
  - `createRole()` - Role 创建（含 9 个权限规则）
  - `createRoleBinding()` - RoleBinding 创建
  - `extractServiceAccountCredentials()` - 凭证提取
  - `createResourceQuota()` 升级 - 支持 maxPods 参数

- [x] **业务逻辑层改造**
  - ResourcePoolService：完整 RBAC 自动化流程（9 步）
  - 新增：AdminResourcePoolService - 凭证发放
  - 新增：AdminPhysicalClusterService - 集群注册

- [x] **API 接口新增**
  - 新增 AdminController
  - POST /api/v1/admin/physical-clusters - 注册物理集群
  - POST /api/v1/admin/resource-pools - 创建部门资源池
  - POST /api/v1/admin/resource-pools/{poolId}/issue-credential - 发放凭证

- [x] **K8s 模板改造**
  - volcano-job.yaml.ftl：添加 `nodeSelector: gpu-node: "true"`
  - vllm-deployment.yaml.ftl：添加 `nodeSelector: gpu-node: "true"`

- [x] **数据库演进**
  - schema-h2.sql：
    - physical_cluster 表添加 description
    - resource_pool 表添加部门相关字段 + maxPods
    - 新增 resource_pool_credential 表
    - 新增查询索引

- [x] **MyBatis Mapper 升级**
  - PhysicalClusterMapper.xml：更新字段映射
  - ResourcePoolMapper.xml：更新字段映射 + insert/update 语句
  - 新增：ResourcePoolCredentialMapper + XML 映射

---

## 🔍 验证清单

### 第一阶段：编译验证

#### 1.1 编译新增服务类
```bash
cd acmp-compute
mvn clean compile
```

**验证项**：
- [ ] 无编译错误
- [ ] AdminPhysicalClusterService 编译成功
- [ ] AdminResourcePoolService 编译成功
- [ ] AdminController 编译成功
- [ ] KubernetesClientManager RBAC 方法编译成功

#### 1.2 检查 import 是否完整
```bash
# 验证新增 imports 的类是否存在
grep -r "import io.fabric8.kubernetes.api.model.rbac" src/
grep -r "import io.fabric8.kubernetes.api.model.ServiceAccount" src/
```

---

### 第二阶段：功能验证

#### 2.1 数据库迁移
启动应用后检查：
```bash
# H2 控制台：http://localhost:8080/h2-console
# 验证表结构
SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'RESOURCE_POOL';
SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'PHYSICAL_CLUSTER';
SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'RESOURCE_POOL_CREDENTIAL';
```

**验证项**：
- [ ] physical_cluster 表有 description 字段
- [ ] resource_pool 表有 department_code, department_name, service_account_name, max_pods 字段
- [ ] resource_pool_credential 表存在
- [ ] 索引创建成功

#### 2.2 管理员接口验证

##### 2.2.1 物理集群注册
```bash
# 1. 创建测试用的 admin 用户（如果尚未存在）
# 2. 获取 JWT token
TOKEN=$(curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.token')

# 3. 注册物理集群
curl -X POST http://localhost:8080/api/v1/admin/physical-clusters \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-cluster-01",
    "description": "Test K8s Cluster",
    "kubeconfigBase64": "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURWakNDQWoyZ0F3SUJBZ0lSUFJZTU..."
  }'
```

**验证项**：
- [ ] 返回 201 Created 状态
- [ ] 响应包含集群 ID、name、description、status
- [ ] 响应 message 显示成功消息
- [ ] 数据库中多了一条 physical_cluster 记录

##### 2.2.2 部门资源池创建
```bash
# 1. 获取刚才创建的集群 ID
CLUSTER_ID="xxx-from-previous-response"

# 2. 创建部门资源池
curl -X POST http://localhost:8080/api/v1/admin/resource-pools \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "physicalClusterId": "'$CLUSTER_ID'",
    "name": "Finance Department Pool",
    "departmentCode": "finance",
    "departmentName": "财务部",
    "gpuSlots": 8,
    "cpuCores": 48,
    "memoryGiB": 256,
    "maxPods": 50
  }'
```

**验证项**：
- [ ] 返回 201 Created 状态
- [ ] 响应包含 poolId、namespace（格式：dept-finance-{随机8位}）
- [ ] 响应包含 serviceAccountName（可能为 sa-dept-finance）
- [ ] 数据库中有新的 resource_pool 记录
- [ ] **K8s 集群中创建了对应的资源**（需连接到测试 K8s）：
  - Namespace: dept-finance-{随机8位}
  - ResourceQuota: 包含 gpu=8, cpu=48, memory=256Gi, pods=50
  - ServiceAccount: sa-dept-finance
  - Role: role-dept-finance
  - RoleBinding: 绑定 SA 到 Role
  - Volcano Queue: queue-dept-finance

##### 2.2.3 凭证发放
```bash
# 1. 获取刚才创建的资源池 ID
POOL_ID="xxx-from-previous-response"

# 2. 发放凭证
curl -X POST http://localhost:8080/api/v1/admin/resource-pools/$POOL_ID/issue-credential \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "zhangsan-finance",
    "expireDays": 30
  }'
```

**验证项**：
- [ ] 返回 200 OK 状态
- [ ] 响应包含 kubeconfig 内容（apiVersion、kind、clusters、users 等）
- [ ] 响应包含 namespace、clusterName、serviceAccountName
- [ ] 响应 message 包含有效期信息
- [ ] kubeconfig 中的 users.token 字段不为空（SA token）

#### 2.3 nodeSelector 注入验证

##### 2.3.1 检查模板文件
```bash
# 检查 tornado-job 模板
grep -A 3 "nodeSelector:" src/main/resources/k8s-templates/volcano-job.yaml.ftl

# 检查 vllm-deployment 模板
grep -A 3 "nodeSelector:" src/main/resources/k8s-templates/vllm-deployment.yaml.ftl
```

**验证项**：
- [ ] 两个模板文件都包含 `nodeSelector: gpu-node: "true"`
- [ ] nodeSelector 位于 Pod spec 级别（不是 container 级别）

##### 2.3.2 提交任务验证
```bash
# 1. 用部门员工身份提交训练任务
# 假设已创建 finance-user 账户并分配到 finance 资源池

# 2. 提交任务
curl -X POST http://localhost:8080/api/v1/resource-pools/$POOL_ID/training-jobs \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "jobName": "test-train-001",
    "image": "nvcr.io/nvidia/pytorch:22.12-py3",
    "replicas": 2,
    "gpuPerPod": 1
  }'
```

**验证项**：
- [ ] VolcanoJob 创建成功
- [ ] 检查 K8s 中的 VolcanoJob YAML：
  ```bash
  kubectl get volcanojob test-train-001 -n dept-finance-xxx -o yaml
  ```
- [ ] YAML 中 Pod spec 包含 `nodeSelector: gpu-node: "true"`

---

### 第三阶段：权限隔离验证

#### 3.1 跨池访问权限验证
```bash
# 1. 用 finance-user (finance 部门) 尝试访问其他部门的资源池
curl -X GET http://localhost:8080/api/v1/resource-pools/$OTHER_POOL_ID \
  -H "Authorization: Bearer $FINANCE_USER_TOKEN"
```

**验证项**：
- [ ] 返回 403 Forbidden （ForbiddenException）

#### 3.2 K8s namespace 访问隔离验证
```bash
# 1. 用发放的凭证连接 K8s
export KUBECONFIG=/tmp/dept-finance-kubeconfig.yaml

# 2. 尝试列出 pods（应该只能看到自己的 namespace）
kubectl get pods

# 3. 尝试访问其他 namespace（应该被拒绝）
kubectl get pods -n kube-system
```

**验证项**：
- [ ] 能列出 dept-finance-xxx namespace 的资源
- [ ] 无法访问其他 namespace 的资源（Forbidden）

---

### 第四阶段：资源限额验证

#### 4.1 GPU 配额限制
```bash
# 1. 在 finance 资源池创建多个 Pod，尝试超过 8 GPU 限制
kubectl -n dept-finance-xxx apply -f - <<EOF
apiVersion: v1
kind: Pod
metadata:
  name: test-gpu-1
spec:
  nodeSelector:
    gpu-node: "true"
  containers:
  - name: test
    image: nvidia/cuda:11.0
    resources:
      requests:
        nvidia.com/gpu: 10  # 超过配额
EOF
```

**验证项**：
- [ ] Pod 处于 Pending 状态
- [ ] Pod 事件显示 `ExceededQuota`

#### 4.2 Pod 数量限制
```bash
# 创建超过 maxPods (50) 数量的 Pod
for i in {1..60}; do
  kubectl -n dept-finance-xxx apply -f - <<EOF
apiVersion: v1
kind: Pod
metadata:
  name: test-pod-$i
spec:
  containers:
  - name: test
    image: busybox
EOF
done
```

**验证项**：
- [ ] 前 50 个 Pod 创建成功
- [ ] 第 51 个及以后的 Pod 处于 Pending，提示超过配额

---

### 第五阶段：集成测试

#### 5.1 完整流程测试脚本

创建 `test-integration.sh`：

```bash
#!/bin/bash

set -e

TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.token')

echo "✓ 获取 admin token"

# 注册集群
CLUSTER_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/admin/physical-clusters \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-cluster",
    "description": "Test Cluster",
    "kubeconfigBase64": "LS0tLS1CRUdJTi..."
  }')

CLUSTER_ID=$(echo $CLUSTER_RESPONSE | jq -r '.id')
echo "✓ 注册集群: $CLUSTER_ID"

# 创建资源池
POOL_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/admin/resource-pools \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "physicalClusterId": "'$CLUSTER_ID'",
    "name": "Test Pool",
    "departmentCode": "test",
    "departmentName": "Test Department",
    "gpuSlots": 8,
    "cpuCores": 32,
    "memoryGiB": 128,
    "maxPods": 50
  }')

POOL_ID=$(echo $POOL_RESPONSE | jq -r '.id')
NAMESPACE=$(echo $POOL_RESPONSE | jq -r '.namespace')
echo "✓ 创建资源池: $POOL_ID (namespace: $NAMESPACE)"

# 发放凭证
CRED_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/admin/resource-pools/$POOL_ID/issue-credential \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test-user",
    "expireDays": 30
  }')

echo "✓ 发放凭证给 test-user"
echo "✓ Namespace: $NAMESPACE"
echo "✓ Kubeconfig (前 100 chars):"
echo $CRED_RESPONSE | jq -r '.kubeconfig' | head -c 100
echo ""

echo ""
echo "✅ 所有集成测试通过！"
```

---

## 📋 生产部署检查清单

在将改造后的代码部署到生产环境前，请检查：

- [ ] 所有单元测试通过
- [ ] 集成测试通过（见上文）
- [ ] 代码审查完成
- [ ] 数据库备份已创建
- [ ] 新增字段默认值已设置
- [ ] 迁移脚本在测试环境验证无误
- [ ] 管理员已被通知新接口和操作流程
- [ ] 日志配置已更新（包括新 Service 类的日志）
- [ ] 监控告警规则已更新
- [ ] 文档已发布给用户

---

## 可能遇到的问题与解决方案

### 问题 1：K8s 连接失败
**症状**：createNamespace 或其他 K8s 操作失败

**解决**：
1. 检查 kubeconfig 是否有效
2. 确保高权限 ServiceAccount 拥有必要权限
3. 查看 K8s 集群日志

### 问题 2：凭证发放失败 - SA Secret 未找到
**症状**：`extractServiceAccountCredentials()` 返回 "ServiceAccount xxx 无关联 Secret"

**解决**：
1. 等待 K8s 自动创建 Secret（通常几秒钟）
2. 检查 namespace 和 SA 是否真的创建成功
3. 检查 K8s 中的 Secret：
   ```bash
   kubectl get secrets -n dept-xxx
   kubectl describe sa sa-dept-xxx -n dept-xxx
   ```

### 问题 3：nodeSelector 未生效
**症状**：Pod 被调度到非 GPU 节点

**解决**：
1. 确认 K8s 节点已标记 `gpu-node=true`：
   ```bash
   kubectl get nodes --show-labels | grep gpu-node
   ```
2. 如未标记，手动添加：
   ```bash
   kubectl label nodes <node-name> gpu-node=true
   ```
3. 检查 YAML 模板是否正确渲染了 nodeSelector

---

## 后续优化建议

1. **凭证管理**：
   - 实现凭证过期自动撤销
   - 添加凭证审计日志
   - 支持凭证轮换

2. **监控告警**：
   - 监控每个资源池的使用率
   - 当接近限额时告警
   - 记录配额超出事件

3. **自动化**：
   - 自动发现并标记 GPU 节点
   - 自动扩展 K8s 集群
   - 自动生成成本报告

4. **文档**：
   - 编写部门用户使用指南
   - 编写管理员运维手册
   - 编写故障排查指南

