# Builder API 改造 - 实施验证清单

## 🔍 快速验证步骤

### 1. 编译验证
```bash
cd c:\Users\jiang\Documents\code\acmp-compute-platform\acmp-compute
mvn clean compile
```
**预期结果**：✅ BUILD SUCCESS（无编译错误）

---

### 2. 依赖验证

#### 检查 K8sTemplateEngine 移除状态
```bash
# 在服务层中搜索 K8sTemplateEngine 的使用
grep -r "K8sTemplateEngine" src/main/java/com/acmp/compute/service/
```
**预期结果**：❌ 无任何匹配（完全移除）

#### 检查 K8sResourceBuilder 导入
```bash
# 验证三个核心 Service 都导入了 K8sResourceBuilder
grep -l "K8sResourceBuilder" src/main/java/com/acmp/compute/service/{ModelDeploymentService,TrainingJobService,ResourcePoolService}.java
```
**预期结果**：✅ 三个文件都返回（都正确导入）

---

### 3. 代码结构验证

#### K8sResourceBuilder 方法检查
```bash
# 确认三个 builder 方法都存在
grep "public static String build" src/main/java/com/acmp/compute/k8s/K8sResourceBuilder.java
```
**预期输出**：
```
public static String buildVllmDeploymentAndService(...)
public static String buildVolcanoJob(...)
public static String buildVolcanoQueue(...)
```

#### 检查各 Service 的改造情况

**ModelDeploymentService**:
```bash
grep -A2 "K8sResourceBuilder.buildVllmDeploymentAndService" src/main/java/com/acmp/compute/service/ModelDeploymentService.java
```
✅ 应该返回该方法的调用

**TrainingJobService**:
```bash
grep -A2 "K8sResourceBuilder.buildVolcanoJob" src/main/java/com/acmp/compute/service/TrainingJobService.java
```
✅ 应该返回该方法的调用

**ResourcePoolService**:
```bash
grep -A2 "K8sResourceBuilder.buildVolcanoQueue" src/main/java/com/acmp/compute/service/ResourcePoolService.java
```
✅ 应该返回该方法的调用

---

### 4. 单元测试验证
```bash
# 运行所有单元测试
mvn test

# 只运行 Service 测试
mvn test -Dtest=*ServiceTest
```
**预期结果**：✅ 所有测试通过

---

### 5. 集成测试场景清单

#### 场景 1: 创建模型部署
```
前置条件：物理集群已注册，资源池已创建
操作：POST /api/model-deployment/deploy
验证：
  ✅ Deployment 创建成功
  ✅ Service 创建成功
  ✅ Pod 调度到 GPU 节点
  ✅ 返回 serviceUrl
```

#### 场景 2: 提交训练任务
```
前置条件：资源池已创建
操作：POST /api/training-job/submit
验证：
  ✅ VolcanoJob 创建成功
  ✅ Job 进入队列
  ✅ 返回 jobId
```

#### 场景 3: 创建资源池
```
前置条件：物理集群已注册
操作：POST /api/admin/resource-pool/create
验证：
  ✅ Namespace 创建
  ✅ ResourceQuota 创建
  ✅ RBAC 创建（SA/Role/RB）
  ✅ Volcano Queue 创建
  ✅ 数据库记录落库
```

---

## 📊 改造影响清单

### 改动的文件清单

| 文件路径 | 修改类型 | 主要改动 | 状态 |
|---------|--------|--------|------|
| `src/main/java/com/acmp/compute/k8s/K8sResourceBuilder.java` | 新建 | 3 个 builder 方法，320+ 行 | ✅ 完成 |
| `src/main/java/com/acmp/compute/service/ModelDeploymentService.java` | 修改 | 移除模板引擎，使用 Builder API | ✅ 完成 |
| `src/main/java/com/acmp/compute/service/TrainingJobService.java` | 修改 | 移除模板引擎，使用 Builder API | ✅ 完成 |
| `src/main/java/com/acmp/compute/service/ResourcePoolService.java` | 修改 | 移除模板引擎，使用 Builder API | ✅ 完成 |

### 未改动但相关的文件

| 文件路径 | 原因 |
|---------|------|
| `src/main/resources/k8s-templates/vllm-deployment.yaml.ftl` | 保留作参考 |
| `src/main/resources/k8s-templates/volcano-job.yaml.ftl` | 保留作参考 |
| `src/main/resources/k8s-templates/volcano-queue.yaml.ftl` | 保留作参考 |
| `src/main/resources/k8s-templates/resource-quota.yaml.ftl` | 保留作参考 |
| `src/main/java/com/acmp/compute/k8s/K8sTemplateEngine.java` | 已停用（可后续删除） |

---

## 🧪 本地测试清单

### 前置准备
- [ ] Java 11+ 已安装
- [ ] Maven 3.6+ 已安装
- [ ] 项目 pom.xml 依赖正确

### 编译阶段
- [ ] `mvn clean compile` 成功
- [ ] 无编译警告（关于 K8sTemplateEngine）
- [ ] K8sResourceBuilder 类被正确加载

### 单元测试阶段
- [ ] `mvn test` 全部通过
- [ ] 至少有 3 个 Builder 相关测试

### 集成测试阶段（需要 K8s 环境）
- [ ] 启动应用：`mvn spring-boot:run`
- [ ] 测试模型部署端点
- [ ] 测试训练任务端点
- [ ] 测试资源池创建端点

### 代码审查清单
- [ ] 无使用 K8sTemplateEngine 的代码遗漏
- [ ] 所有 Builder 调用参数正确
- [ ] 异常处理完整
- [ ] 日志记录充分（debug/info/error）

---

## 🔧 常见问题排查

### 问题 1: 编译错误 "K8sResourceBuilder cannot be found"
**原因**：K8sResourceBuilder.java 未被正确创建  
**排查**：
```bash
ls -l src/main/java/com/acmp/compute/k8s/K8sResourceBuilder.java
```
**解决**：确认文件存在，重新执行 `mvn clean`

### 问题 2: 编译错误 "cannot find symbol: method buildVllmDeploymentAndService"
**原因**：K8sResourceBuilder 中方法签名不匹配  
**排查**：
```bash
grep "public static String buildVllmDeploymentAndService" \
  src/main/java/com/acmp/compute/k8s/K8sResourceBuilder.java
```
**解决**：检查方法是否按照 Builder API 正确定义

### 问题 3: 测试失败 "templateEngine not found"
**原因**：Service 中仍有模板引擎的注入  
**排查**：
```bash
grep "@Autowired.*K8sTemplateEngine\|private.*K8sTemplateEngine" \
  src/main/java/com/acmp/compute/service/*.java
```
**解决**：完全移除模板引擎的依赖声明

### 问题 4: 运行时错误 "YAML 序列化失败"
**原因**：Serialization.asYaml() 在 K8sResourceBuilder 中失败  
**排查**：查看日志，确认生成的 Map 结构正确  
**解决**：检查参数值（null 值、类型转换等）

---

## 📈 性能验证

### 测试方法
```java
@Test
public void performanceTest() {
    // 模板方式（此处为参考，代码已移除）
    long templateStart = System.nanoTime();
    for (int i = 0; i < 1000; i++) {
        // K8sTemplateEngine.render(...)
    }
    long templateTime = System.nanoTime() - templateStart;
    
    // Builder 方式
    long builderStart = System.nanoTime();
    for (int i = 0; i < 1000; i++) {
        K8sResourceBuilder.buildVllmDeploymentAndService(...);
    }
    long builderTime = System.nanoTime() - builderStart;
    
    // Builder 方式应该更快
    assertTrue(builderTime < templateTime);
}
```

### 预期结果
| 操作 | 模板方式 | Builder 方式 | 改进 |
|------|---------|-----------|------|
| 1000 次构建 | ~50000ms | ~10000ms | 快 5 倍 |

---

## 📝 提交前检查清单

### 代码质量
- [ ] 代码按照 Google Java Style Guide 格式化
- [ ] 添加了必要的 Javadoc 注释
- [ ] 异常处理完整
- [ ] 日志记录使用 log.error() 和 log.debug()

### 功能正确性
- [ ] 生成的 YAML 结构与原模板等价
- [ ] 所有参数都被正确传入和处理
- [ ] nodeSelector 和资源限制正确设置

### 向后兼容性
- [ ] 生成的 K8s 资源 API 版本正确
- [ ] YAML 字段顺序不影响功能（K8s 标准）
- [ ] 数据库模式无变化

### 文档完整性
- [ ] Javadoc 清晰说明方法用途
- [ ] 参数文档齐全
- [ ] 返回值说明准确
- [ ] 异常说明完整

---

## 🚀 后续建议

### 立即可做
1. 运行 `mvn clean test` 验证
2. 启动应用，测试三个核心端点
3. 查看日志是否有异常

### 短期改进（1-2 周）
1. 添加更详细的单元测试
2. 编写 Builder API 的使用文档
3. 标记 K8sTemplateEngine 为 @Deprecated

### 中期改进（1-3 个月）
1. 从 pom.xml 移除 freemarker 依赖（如无其他用途）
2. 删除 K8sTemplateEngine 类
3. 删除 Freemarker 模板文件

### 长期维护（3 个月+）
1. 持续监控 fabric8 库更新
2. 适配新的 K8s 版本
3. 定期审核 Builder 方法的性能

---

## ✅ 验证通过标志

当以下条件都满足时，表示改造验证通过：

- ✅ `mvn clean compile` 无错误
- ✅ `mvn test` 全部用例通过（≥ 3 个）
- ✅ 应用成功启动
- ✅ 模型部署端点可正常调用
- ✅ 训练任务端点可正常调用
- ✅ 资源池创建流程完整
- ✅ 所有日志显示 `✓ 成功` 标志
- ✅ K8s 资源创建成功并运行中

---

## 📞 技术支持

如遇到问题，请按顺序检查：

1. **编译问题** → 查看 `mvn clean compile` 错误信息
2. **运行时问题** → 查看应用日志中的 ERROR 和 WARN
3. **功能问题** → 检查 K8s 资源是否成功创建（`kubectl get pods/deployments/jobs`）
4. **性能问题** → 查看构建方法的执行时间和内存占用

---

**最后更新**：改造完成  
**改造人员**：架构优化团队  
**质保状态**：待测试验证  

