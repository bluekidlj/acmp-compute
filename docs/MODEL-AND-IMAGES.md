# 模型与镜像本地化说明

## 1. 模型与权重来源

当前平台**仅支持从本地获取**模型与权重（宿主机目录或集群内 PVC），**暂不考虑 OBS、SWR** 等对象存储或镜像仓存权重。

- 部署 vLLM 时，`modelIdOrPath` 表示**容器内**的模型路径（如 `/models` 或 `/models/qwen3`）。
- 若权重在宿主机上，需通过 **hostPath** 或 **PVC** 挂载到容器内，并在请求中填写 `hostModelPath`（宿主机路径）与 `modelIdOrPath`（容器内挂载点路径）。

## 2. 如何将镜像下载到本地云主机

### 2.1 使用 Docker

在云主机上执行：

```bash
docker pull vllm/vllm-openai:latest
# 或指定版本
docker pull vllm/vllm-openai:v0.4.0
```

### 2.2 使用 containerd（K8s 常用）

若集群使用 containerd 作为容器运行时：

```bash
ctr -n k8s.io image pull docker.io/vllm/vllm-openai:latest
```

### 2.3 多节点集群

- 需在**每个会调度到的工作节点**上拉取同一镜像，或
- 搭建**私有镜像仓库**，节点从该仓库拉取（避免外网或统一从一台机同步）。

### 2.4 无外网节点（离线）

在一台有网络的机器上导出镜像，再在目标节点导入：

```bash
# 有网机器
docker save vllm/vllm-openai:latest -o vllm-openai.tar
# 拷贝 vllm-openai.tar 到目标节点后
docker load -i vllm-openai.tar
# 或 containerd
ctr -n k8s.io image import vllm-openai.tar
```

## 3. 有权重 vs 无权重

### 3.1 有权重（with_weights）

- **方式 A**：使用**已将模型权重打进去的自定义镜像**（单镜像包含 vLLM + 权重）。部署时 `modelIdOrPath` 指向镜像内路径（如 `/opt/model`），**无需** `hostModelPath`。
- **方式 B**：使用标准 vLLM 镜像 + **hostPath/PVC** 挂载宿主机上已下载好的权重目录（如 `/data/models/Qwen3`）。请求中填写：
  - `hostModelPath`: `/data/models/Qwen3`（宿主机路径）
  - `modelIdOrPath`: `/models`（容器内挂载点，挂载后权重出现在 `/models` 下）

### 3.2 无权重（without_weights）

- 仅拉取 vLLM **运行时镜像**（如 `vllm/vllm-openai`），镜像**不包含**权重。
- 部署时**必须**通过 `hostModelPath` 挂载本地权重目录，并设置 `modelIdOrPath` 为该挂载路径（如 `/models`），否则 vLLM 启动会报错缺模型。
- **步骤**：先在宿主机准备好权重目录（例如从 HuggingFace 下载到 `/data/models/Qwen3`），再在部署请求中填写 `hostModelPath=/data/models/Qwen3`、`modelIdOrPath=/models`、`modelSource=without_weights`。
