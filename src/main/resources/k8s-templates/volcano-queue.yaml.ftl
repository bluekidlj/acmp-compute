# Volcano Queue 模板：队列配额，与 ResourcePool 容量一致
# 变量: queueName, gpuSlots, cpuCores, memoryGiB
apiVersion: scheduling.volcano.sh/v1beta1
kind: Queue
metadata:
  name: ${queueName}
spec:
  capability:
    nvidia.com/gpu: ${gpuSlots}
    cpu: ${cpuCores}
    memory: ${memoryGiB}Gi
  weight: 1
  reclaimable: true
