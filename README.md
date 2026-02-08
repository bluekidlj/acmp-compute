# acmp-compute

AI Compute Platform（显卡资源管理与任务调度平台）后端 Demo。基于 Spring Boot 2.7，管理多 K8s 集群与逻辑资源池，支持 vLLM 模型服务部署（如 Qwen3）与 Volcano 训练任务提交。

## 技术栈

- Java 11、Spring Boot 2.7
- fabric8 Kubernetes Client 6.13.x
- H2 + MyBatis
- Spring Security + JWT
- Freemarker（K8s YAML 模板）
- Lombok、Maven

## 快速开始

### 本地运行

```bash
mvn spring-boot:run
```

- 默认端口：8080
- H2 控制台：http://localhost:8080/h2-console（JDBC URL: `jdbc:h2:file:./data/acmp`，用户名 `sa`，密码空）
- 默认管理员：`admin` / `admin123`

### Docker 运行

```bash
docker build -t acmp-compute:latest .
docker run -p 8080:8080 \
  -e JWT_SECRET=your-secret \
  -e AES_KEY=acmp32byteskey!!!!!!!!!!!!!!!!! \
  acmp-compute:latest
```

详见 [docs/DEPLOY.md](docs/DEPLOY.md)。

## 主要 API

| 接口 | 说明 |
|------|------|
| POST /api/v1/auth/login | 登录获取 JWT |
| POST/GET/DELETE /api/v1/physical-clusters | 物理集群注册/列表/删除 |
| GET /api/v1/physical-clusters/{id}/capacity | 集群容量 |
| POST/GET /api/v1/resource-pools | 逻辑资源池创建/列表 |
| POST/GET/GET/DELETE .../resource-pools/{poolId}/model-deployments | vLLM 部署/列表/状态/删除 |
| POST .../resource-pools/{poolId}/training-jobs | 提交 Volcano 训练任务 |

## 文档

- [EXAMPLE-REQUEST.md](docs/EXAMPLE-REQUEST.md) — 示例 HTTP 报文与 curl
- [REQUEST-FLOW.md](docs/REQUEST-FLOW.md) — 请求到 K8s 的流程图
- [MODEL-AND-IMAGES.md](docs/MODEL-AND-IMAGES.md) — 模型与镜像本地化（有权重/无权重、镜像拉取）
- [HAMI-VOLCANO.md](docs/HAMI-VOLCANO.md) — HAMi、Volcano 在项目中的定位
- [DEPLOY.md](docs/DEPLOY.md) — Docker 部署与启动命令

## 模板参考示例

`src/main/resources/k8s-templates-example/` 下提供模板渲染后的参考 YAML：

- `resource-quota-example.yaml`
- `vllm-deployment-example.yaml`

便于对照 Freemarker 模板（`k8s-templates/*.ftl`）与最终生成的资源定义。
