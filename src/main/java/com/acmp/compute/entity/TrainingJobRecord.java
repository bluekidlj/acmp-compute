package com.acmp.compute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 训练任务记录（便于列表展示，与 K8s VolcanoJob 对应） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingJobRecord {
    private String id;
    private String resourcePoolId;
    private String k8sJobName;
    private String jobName;
    private String status;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
