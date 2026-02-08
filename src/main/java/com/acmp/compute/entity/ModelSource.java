package com.acmp.compute.entity;

/**
 * 模型权重来源：有权重（镜像内或挂载） / 无权重（仅运行时镜像，须挂载本地权重）。
 */
public enum ModelSource {
    WITH_WEIGHTS,
    WITHOUT_WEIGHTS
}
