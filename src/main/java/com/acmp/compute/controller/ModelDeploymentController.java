package com.acmp.compute.controller;

import com.acmp.compute.dto.ModelDeploymentResponse;
import com.acmp.compute.dto.VllmDeployRequest;
import com.acmp.compute.service.ModelDeploymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * vLLM 模型服务部署：部署为最基础用户权限，拥有该 pool 即可部署。
 */
@RestController
@RequestMapping("/api/v1/resource-pools/{poolId}/model-deployments")
@RequiredArgsConstructor
public class ModelDeploymentController {

    private final ModelDeploymentService modelDeploymentService;

    @PostMapping
    public ResponseEntity<ModelDeploymentResponse> deploy(
            @PathVariable String poolId,
            @Valid @RequestBody VllmDeployRequest request) {
        ModelDeploymentResponse resp = modelDeploymentService.deploy(poolId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping
    public ResponseEntity<List<ModelDeploymentResponse>> list(@PathVariable String poolId) {
        return ResponseEntity.ok(modelDeploymentService.listByPool(poolId));
    }

    @GetMapping("/{deploymentId}")
    public ResponseEntity<ModelDeploymentResponse> getStatus(
            @PathVariable String poolId,
            @PathVariable String deploymentId) {
        return ResponseEntity.ok(modelDeploymentService.getStatus(poolId, deploymentId));
    }

    @DeleteMapping("/{deploymentId}")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable String poolId,
            @PathVariable String deploymentId) {
        modelDeploymentService.delete(poolId, deploymentId);
        return ResponseEntity.ok(Map.of("message", "已删除部署"));
    }
}
