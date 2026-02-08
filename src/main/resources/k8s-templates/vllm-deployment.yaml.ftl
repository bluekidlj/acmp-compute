# vLLM 模型服务：Deployment + Service（模型与权重从本地获取，挂载 hostPath 或 PVC）
# 变量: deploymentName, serviceName, namespace, image, modelIdOrPath, gpuPerReplica, gpumemMb, gpucores, replicas, hostModelPath(可选)
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${deploymentName}
  namespace: ${namespace}
  labels:
    app: vllm
    model: ${modelName!"vllm"}
spec:
  replicas: ${replicas}
  selector:
    matchLabels:
      app: vllm
      deployment: ${deploymentName}
  template:
    metadata:
      labels:
        app: vllm
        deployment: ${deploymentName}
    spec:
      containers:
      - name: vllm
        image: ${image}
        ports:
        - containerPort: 8000
          name: http
        env:
        - name: VLLM_MODEL
          value: "${modelIdOrPath!''}"
        - name: NVIDIA_VISIBLE_DEVICES
          value: "all"
        resources:
          limits:
            nvidia.com/gpu: "${gpuPerReplica}"
            <#if gpumemMb?? && (gpumemMb > 0)>
            nvidia.com/gpumem: "${gpumemMb}"
            </#if>
            <#if gpucores?? && (gpucores > 0)>
            nvidia.com/gpucores: "${gpucores}"
            </#if>
          requests:
            nvidia.com/gpu: "${gpuPerReplica}"
        <#if hostModelPath?? && (hostModelPath?length > 0)>
        volumeMounts:
        - name: model-data
          mountPath: /models
        </#if>
        readinessProbe:
          httpGet:
            path: /health
            port: 8000
          initialDelaySeconds: 60
          periodSeconds: 10
      <#if hostModelPath?? && (hostModelPath?length > 0)>
      volumes:
      - name: model-data
        hostPath:
          path: ${hostModelPath}
          type: Directory
      </#if>
---
apiVersion: v1
kind: Service
metadata:
  name: ${serviceName}
  namespace: ${namespace}
spec:
  selector:
    app: vllm
    deployment: ${deploymentName}
  ports:
  - port: 8000
    targetPort: 8000
    name: http
  type: ClusterIP
