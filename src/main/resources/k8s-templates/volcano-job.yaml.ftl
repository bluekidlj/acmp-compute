# VolcanoJob 模板：训练任务，支持 gang scheduling 与 HAMi vGPU 资源
# 变量: jobName, namespace, queueName, minAvailable, image, replicas, gpuPerPod, gpuMemPerPod, gpuCoresPerPod, command
apiVersion: batch.volcano.sh/v1alpha1
kind: VolcanoJob
metadata:
  name: ${jobName}
  namespace: ${namespace}
spec:
  minAvailable: ${minAvailable}
  schedulerName: volcano
  queue: ${queueName}
  tasks:
  - name: worker
    replicas: ${replicas}
    minAvailable: ${replicas}
    template:
      spec:
        restartPolicy: Never
        containers:
        - name: worker
          image: ${image}
          <#if command?? && (command?size > 0)>
          command:
          <#list command as c>
          - ${c}
          </#list>
          </#if>
          resources:
            limits:
              nvidia.com/gpu: "${gpuPerPod}"
              <#if gpuMemPerPod?? && (gpuMemPerPod > 0)>
              nvidia.com/gpumem: "${gpuMemPerPod}"
              </#if>
              <#if gpuCoresPerPod?? && (gpuCoresPerPod > 0)>
              nvidia.com/gpucores: "${gpuCoresPerPod}"
              </#if>
            requests:
              nvidia.com/gpu: "${gpuPerPod}"
