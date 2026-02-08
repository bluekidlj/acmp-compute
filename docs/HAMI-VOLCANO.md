# HAMi 与 Volcano 在项目中的定位与作用

## 1. Volcano 在项目中的定位

- **批调度器**：负责队列（Queue）与作业（VolcanoJob）的 **gang scheduling** 与队列配额。
- 逻辑资源池创建时，会为每个池创建一个 **Volcano Queue**，其 `spec.capability` 与资源池的 GPU/CPU/Memory 一致，用于队列级资源限制与调度策略。
- 训练任务以 **VolcanoJob** 形式提交，指定 `schedulerName: volcano` 和 `queue`，实现多副本协同调度（minAvailable）。

## 2. HAMi 在项目中的定位

- **GPU 共享 / vGPU 设备插件**：提供 `nvidia.com/gpumem`、`nvidia.com/gpucores` 等扩展资源。
- 平台**不**在应用代码里“配置 HAMi”，而是确保 Pod/VolcanoJob 的 `resources.limits` 使用 HAMi 要求的资源名（`nvidia.com/gpu`、`nvidia.com/gpumem`、`nvidia.com/gpucores`）。
- 集群侧需**预装** HAMi device plugin 与调度扩展；平台只负责在 YAML 中声明这些资源。

## 3. 与 ResourceQuota、Namespace 的关系

- **Namespace + ResourceQuota**：做池化隔离，限制每个逻辑资源池（namespace）内的 GPU/CPU/Memory 总量。
- **Volcano Queue**：做队列级资源与调度策略，与 ResourcePool 一一对应。
- **vLLM 模型服务**：在逻辑资源池对应 namespace 下以标准 **Deployment + Service** 部署，资源声明与 HAMi/GPU 一致；vLLM 在平台中的定位是**推理服务运行时**。

## 4. Pod/VolcanoJob 中资源字段说明

- `nvidia.com/gpu`：GPU 数量（或 vGPU 槽数）。
- `nvidia.com/gpumem`：显存，单位 MiB（HAMi 时使用）。
- `nvidia.com/gpucores`：GPU 算力占比等（HAMi 时使用）。

模板中已按需包含上述 limits/requests，按请求参数传入即可。
