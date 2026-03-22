# 数据迁移与平滑升级指南

## 📋 升级前准备清单

- [ ] 备份现有数据库
- [ ] 停止所有运行中的任务
- [ ] 通知所有用户升级时间
- [ ] 准备回滚计划
- [ ] 测试环境完整验证（见 VERIFICATION-CHECKLIST.md）

---

## 🔄 分阶段升级策略

### 阶段 1：代码部署（无数据变化）

**操作步骤**：
1. Pull 新代码
2. `mvn clean install`
3. 编译无错误后，部署新 JAR
4. 应用会自动执行 H2 迁移脚本（schema-h2.sql）

**验证**：
```bash
# 检查日志
tail -f logs/acmp-compute.log | grep "CREATE TABLE"

# 验证新表和字段是否创建
sqlite> .schema resource_pool_credential
sqlite> .schema resource_pool  # 检查新字段
```

---

## 🛠️ 数据库迁移

### 自动迁移（H2）

H2 数据库支持 `IF NOT EXISTS` 语句，所以：
- ✅ 新表会自动创建
- ✅ 新字段会自动添加
- ✅ 现有数据保持不变

**验证迁移成功**：
```sql
-- H2 Console: http://localhost:8080/h2-console

-- 1. 验证新字段
SELECT department_code, department_name, service_account_name, max_pods 
FROM resource_pool 
LIMIT 1;
-- 预期：返回 4 列（可能都是 NULL，因为现有数据没有值）

-- 2. 验证新表
SELECT * FROM resource_pool_credential;
-- 预期：空表，但结构存在

-- 3. 验证现有数据完整
SELECT COUNT(*) FROM resource_pool;
-- 预期：与升级前数量相同
```

### 为现有资源池填充默认值

**问题**：升级前创建的 resource_pool 记录缺少新字段值

**解决方案**：运行迁移脚本

```sql
-- 填充现有资源池的部门代码（基于 name 和 id）
UPDATE resource_pool 
SET department_code = CONCAT('dept_', SUBSTRING(id, 1, 8)),
    department_name = name,
    service_account_name = CONCAT('sa-', SUBSTRING(id, 1, 8)),
    max_pods = 50
WHERE department_code IS NULL;

-- 验证
SELECT COUNT(*) FROM resource_pool WHERE department_code IS NOT NULL;
-- 预期：与总数相同
```

### MySQL 迁移（仅适用于迁移到 MySQL）

如果生产环境使用 MySQL，需要手动执行如下迁移：

```sql
-- 1. 为 physical_cluster 添加 description 列
ALTER TABLE physical_cluster 
ADD COLUMN description VARCHAR(512) DEFAULT NULL;

-- 2. 为 resource_pool 添加新列
ALTER TABLE resource_pool 
ADD COLUMN department_code VARCHAR(64) NOT NULL DEFAULT CONCAT('dept_', SUBSTRING(id, 1, 8)),
ADD COLUMN department_name VARCHAR(255) DEFAULT NULL,
ADD COLUMN service_account_name VARCHAR(255) DEFAULT NULL,
ADD COLUMN max_pods INT DEFAULT 50;

-- 3. 添加 UNIQUE 约束到 namespace
ALTER TABLE resource_pool 
ADD CONSTRAINT uk_resource_pool_namespace UNIQUE(namespace);

-- 4. 创建新表：resource_pool_credential
CREATE TABLE resource_pool_credential (
    id VARCHAR(36) PRIMARY KEY,
    resource_pool_id VARCHAR(36) NOT NULL,
    username VARCHAR(128) NOT NULL,
    kubeconfig LONGTEXT NOT NULL,
    expire_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (resource_pool_id) REFERENCES resource_pool(id)
);

-- 5. 创建索引
CREATE INDEX idx_resource_pool_dept ON resource_pool(department_code);
CREATE INDEX idx_resource_pool_cluster ON resource_pool(physical_cluster_id);
CREATE INDEX idx_credential_pool ON resource_pool_credential(resource_pool_id);
```

---

## 🔀 API 兼容性

### 1. 现有用户代码是否受影响？

| 场景 | 影响 | 说明 |
|------|------|------|
| 调用 POST /api/v1/resource-pools | ⚠️ **需要更新** | 新增必填字段：departmentCode, departmentName |
| 调用 GET /api/v1/resource-pools | ✅ 兼容 | 响应新增字段（可忽略） |
| 调用 POST /api/v1/resource-pools/{id}/training-jobs | ✅ 兼容 | 自动注入 nodeSelector（对用户透明） |

### 2. 升级后的 API 调用示例

#### 旧方式（升级后需改为新方式）
```json
POST /api/v1/resource-pools
{
  "physicalClusterId": "xxx",
  "name": "My Pool",
  "gpuSlots": 8,
  "cpuCores": 48,
  "memoryGiB": 256
}
```

#### 新方式（升级后必须使用）
```json
POST /api/v1/admin/resource-pools
{
  "physicalClusterId": "xxx",
  "name": "My Pool",
  "departmentCode": "finance",           ← 必填（新增）
  "departmentName": "Finance Dept",      ← 必填（新增）
  "gpuSlots": 8,
  "cpuCores": 48,
  "memoryGiB": 256,
  "maxPods": 50                          ← 可选（新增，默认 50）
}
```

**重要**：升级后创建资源池通过 `/api/v1/admin/resource-pools` 而不是 `/api/v1/resource-pools`

---

## 🌐 多环境升级顺序

### 推荐升级顺序

1. **Dev 环境** → 完整测试（3-5 天）
2. **Test 环境** → 用户验收测试 UAT（5-7 天）
3. **Staging 环境** → 性能测试、容量规划（2-3 天）
4. **Production 环境** → 分批灰度发布或蓝绿部署（1 天）

### 灰度发布策略

如果生产环境采用分批发布：

```bash
# Day 1: 部署到 20% 的节点
kubectl set image deployment/acmp-compute app=acmp:v2.0 --record

# Day 2: 监控性能指标，若无异常，扩展到 50%
kubectl scale deployment acmp-compute --replicas 5  # 增加副本数

# Day 3: 全量发布到 100%
kubectl scale deployment acmp-compute --replicas 10
```

---

## 📊 升级前后对比

### 升级前

```
资源池创建流程：
API → ResourcePoolService.create()
  ├─ 创建 Namespace
  ├─ 创建 ResourceQuota（通过 YAML 模板）
  └─ 写入数据库
  
结果：
- Namespace: pool-uuid123
- 无 RBAC 资源
- 无凭证管理
- nodeSelector: 无
```

### 升级后

```
资源池创建流程：
API → AdminResourcePoolService → ResourcePoolService.create()
  ├─ 创建 Namespace（dept-{dept}-{random}）
  ├─ 创建 ResourceQuota（GPU/CPU/Memory/Pods）
  ├─ 创建 ServiceAccount
  ├─ 创建 Role（9 个权限规则）
  ├─ 创建 RoleBinding
  ├─ 创建 Volcano Queue
  └─ 写入数据库
  
凭证发放流程：
API → AdminResourcePoolService.issueCredential()
  ├─ 从 SA Secret 提取 token
  ├─ 生成 kubeconfig
  └─ 返回给用户
  
结果：
- Namespace: dept-finance-abc123
- RBAC: SA + Role + RoleBinding
- 凭证：完整 kubeconfig（含 token）
- nodeSelector：所有 Pod 强制 gpu-node=true
```

---

## 🚨 常见升级问题与解决

### 问题 1：升级后旧 API 失效
**现象**：调用 POST /api/v1/resource-pools 返回 400 错误

**原因**：新增必填字段

**解决**：
1. 更新客户端代码，使用新的字段
2. 或使用新的 API 端点 `/api/v1/admin/resource-pools`

### 问题 2：现有 Pod 没有 nodeSelector
**现象**：升级前创建的 Pod 仍在所有节点运行

**原因**：nodeSelector 只对新创建的 Pod 生效

**解决**：
1. 删除旧 Pod，等待重建（自动获取 nodeSelector）
2. 或手动滚动更新 Deployment：
   ```bash
   kubectl rollout restart deployment/<name> -n <namespace>
   ```

### 问题 3：RBAC 权限不足
**现象**：部门员工无法操作资源（Forbidden）

**原因**：自动创建的 Role 权限不足

**解决**：
1. 检查 Role 定义是否包含所需权限
2. 编辑 KubernetesClientManager.createRole() 方法添加权限
3. 删除旧 Role，重新创建资源池

### 问题 4：凭证提取失败
**现象**：issue-credential API 返回 "ServiceAccount ... 无关联 Secret"

**原因**：SA Secret 未及时创建

**解决**：
1. 等待 30 秒后重试
2. 检查 namespace 中的 Secret：
   ```bash
   kubectl get secrets -n <namespace>
   ```
3. 若 Secret 确实不存在，检查 K8s 日志

---

## ✅ 升级完成验证清单

升级完成后，逐项验证：

- [ ] 应用启动无错误
- [ ] H2 console 可访问
- [ ] 新表 resource_pool_credential 存在
- [ ] 现有 resource_pool 记录保持不变
- [ ] 管理员可注册新物理集群
- [ ] 管理员可创建部门资源池
- [ ] 管理员可发放部门凭证
- [ ] 部门员工可用凭证访问 K8s
- [ ] 新创建的 Pod 包含 nodeSelector: gpu-node=true
- [ ] 工作流测试完全通过（见 QUICK-START.md 的 6 步测试）

---

## 📞 升级中遇到问题

### 快速诊断

1. **查看应用日志**
   ```bash
   tail -f logs/acmp-compute.log
   ```
   检查是否有 ERROR 或 Exception

2. **检查数据库连接**
   ```bash
   # H2 Console URL
   curl http://localhost:8080/h2-console
   ```

3. **验证 K8s 连接**
   ```java
   // 在 AdminPhysicalClusterService 中添加日志调试
   log.debug("Validating kubeconfig...");
   ```

4. **查看业务日志**
   ```bash
   # ResourcePoolService 的创建日志
   grep "已成功创建部门资源池" logs/acmp-compute.log
   ```

### 联系支持

记录以下信息：
- 错误日志片段
- 执行的 API 请求
- 环境信息（Java 版本、K8s 版本、数据库类型）

---

## 🔄 回滚计划

如升级出现严重问题，可执行回滚：

### 快速回滚（< 5 分钟）

```bash
# 1. 停止新版应用
docker stop acmp-compute-v2.0

# 2. 启动旧版应用
docker start acmp-compute-v1.0

# 3. 验证旧版可用
curl http://localhost:8080/api/v1/resource-pools
```

### 完整回滚（包括数据）

```bash
# 1. 备份新版数据
cp data/acmp.db data/acmp.db.backup.v2.0

# 2. 恢复旧版数据
cp data/acmp.db.backup.v1.0 data/acmp.db

# 3. 启动旧版应用
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"
```

**注意**：升级后新增的 resource_pool_credential 表中的凭证在回滚后会丢失

---

## 📚 升级相关文档

- 详细改造文档：[UPGRADE-SUMMARY.md](UPGRADE-SUMMARY.md)
- 验证清单：[VERIFICATION-CHECKLIST.md](VERIFICATION-CHECKLIST.md)
- 快速开始：[QUICK-START.md](QUICK-START.md)
- 本文档：[MIGRATION-GUIDE.md](MIGRATION-GUIDE.md)

