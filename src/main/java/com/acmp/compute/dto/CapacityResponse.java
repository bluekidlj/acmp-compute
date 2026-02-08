package com.acmp.compute.dto;

import lombok.Builder;
import lombok.Data;

/** 集群或资源池容量汇总（GPU/CPU/Memory） */
@Data
@Builder
public class CapacityResponse {
    private Long gpuSlots;
    private String cpu;
    private String memory;
}
