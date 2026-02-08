# ResourceQuota 模板：限制 namespace 内 GPU/CPU/Memory
# 变量: namespace, gpuSlots, cpuCores, memoryGiB
apiVersion: v1
kind: ResourceQuota
metadata:
  name: ${namespace}-quota
  namespace: ${namespace}
spec:
  hard:
    cpu: "${cpuCores}"
    memory: "${memoryGiB}Gi"
    nvidia.com/gpu: "${gpuSlots}"
    pods: "200"
