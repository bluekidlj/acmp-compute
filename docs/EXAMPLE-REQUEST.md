# 示例请求：从登录到部署 vLLM 模型服务

以下为参考 HTTP 报文与 curl 命令，用于本地或 Docker 启动后联调。

## 1. 启动服务

```bash
# 本地
mvn spring-boot:run

# 或 Docker
docker build -t acmp-compute:latest .
docker run -p 8080:8080 -e JWT_SECRET=your-secret -e AES_KEY=acmp32byteskey!!!!!!!!!!!!!!!!! acmp-compute:latest
```

## 2. 登录获取 JWT

**请求：**

```http
POST /api/v1/auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{"username":"admin","password":"admin123"}
```

**curl：**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

**响应示例：**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "admin",
  "role": "PLATFORM_ADMIN",
  "expiresInMs": 86400000
}
```

后续请求在 Header 中携带：`Authorization: Bearer <token>`。

## 3. 注册物理集群（需 PLATFORM_ADMIN）

**请求：**

```http
POST /api/v1/physical-clusters HTTP/1.1
Host: localhost:8080
Authorization: Bearer <token>
Content-Type: application/json

{"name":"my-k8s","kubeconfigBase64":"<base64 或原始 kubeconfig 内容>"}
```

**curl：**

```bash
export TOKEN="<上一步返回的 token>"
curl -s -X POST http://localhost:8080/api/v1/physical-clusters \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"my-k8s","kubeconfigBase64":"<粘贴 kubeconfig 或 Base64>"}'
```

## 4. 创建逻辑资源池（需 PLATFORM_ADMIN / ORG_ADMIN）

**请求：**

```http
POST /api/v1/resource-pools HTTP/1.1
Host: localhost:8080
Authorization: Bearer <token>
Content-Type: application/json

{"physicalClusterId":"<上一步返回的 id>","name":"pool-a","gpuSlots":8,"cpuCores":32,"memoryGiB":128}
```

**curl：**

```bash
curl -s -X POST http://localhost:8080/api/v1/resource-pools \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"physicalClusterId":"<集群id>","name":"pool-a","gpuSlots":8,"cpuCores":32,"memoryGiB":128}'
```

## 5. 在逻辑资源池部署 vLLM 模型服务（Qwen3，有权重挂载）

**请求：**

```http
POST /api/v1/resource-pools/<poolId>/model-deployments HTTP/1.1
Host: localhost:8080
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "qwen3-svc",
  "modelName": "Qwen3",
  "modelSource": "with_weights",
  "modelIdOrPath": "/models",
  "vllmImage": "vllm/vllm-openai:latest",
  "gpuPerReplica": 2,
  "gpumemMb": 8192,
  "gpucores": 100,
  "replicas": 1,
  "hostModelPath": "/data/models/Qwen3"
}
```

**curl：**

```bash
curl -s -X POST "http://localhost:8080/api/v1/resource-pools/<poolId>/model-deployments" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "qwen3-svc",
    "modelName": "Qwen3",
    "modelSource": "with_weights",
    "modelIdOrPath": "/models",
    "vllmImage": "vllm/vllm-openai:latest",
    "gpuPerReplica": 2,
    "gpumemMb": 8192,
    "gpucores": 100,
    "replicas": 1,
    "hostModelPath": "/data/models/Qwen3"
  }'
```

**响应示例：**

```json
{
  "id": "uuid",
  "resourcePoolId": "pool-id",
  "name": "qwen3-svc",
  "modelName": "Qwen3",
  "modelSource": "with_weights",
  "status": "running",
  "serviceUrl": "http://vllm-qwen3-svc-svc.pool-xxx.svc.cluster.local:8000",
  "createdAt": "...",
  "updatedAt": "..."
}
```

## 6. 查询部署状态

**请求：**

```http
GET /api/v1/resource-pools/<poolId>/model-deployments/<deploymentId> HTTP/1.1
Host: localhost:8080
Authorization: Bearer <token>
```

**curl：**

```bash
curl -s "http://localhost:8080/api/v1/resource-pools/<poolId>/model-deployments/<deploymentId>" \
  -H "Authorization: Bearer $TOKEN"
```

## 7. 提交训练任务（VolcanoJob）

**请求：**

```http
POST /api/v1/resource-pools/<poolId>/training-jobs HTTP/1.1
Host: localhost:8080
Authorization: Bearer <token>
Content-Type: application/json

{
  "jobName": "train-demo",
  "image": "nvcr.io/nvidia/pytorch:24.08-py3",
  "replicas": 2,
  "gpuPerPod": 1,
  "gpuMemPerPod": 8192,
  "gpuCoresPerPod": 100,
  "command": ["python", "train.py", "--epochs=10"]
}
```

**curl：**

```bash
curl -s -X POST "http://localhost:8080/api/v1/resource-pools/<poolId>/training-jobs" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"jobName":"train-demo","image":"nvcr.io/nvidia/pytorch:24.08-py3","replicas":2,"gpuPerPod":1,"gpuMemPerPod":8192,"gpuCoresPerPod":100,"command":["python","train.py"]}'
```

## 8. 查询集群容量

```bash
curl -s "http://localhost:8080/api/v1/physical-clusters/<clusterId>/capacity" \
  -H "Authorization: Bearer $TOKEN"
```
