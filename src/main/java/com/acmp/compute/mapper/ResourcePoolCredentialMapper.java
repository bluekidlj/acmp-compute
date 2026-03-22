package com.acmp.compute.mapper;

import com.acmp.compute.entity.ResourcePoolCredential;
import java.util.List;
import java.util.Optional;

/**
 * 资源池凭证 Mapper：管理部门用户的 K8s 访问凭证。
 */
public interface ResourcePoolCredentialMapper {
    void insert(ResourcePoolCredential credential);
    Optional<ResourcePoolCredential> findById(String id);
    List<ResourcePoolCredential> findByResourcePoolId(String resourcePoolId);
    List<ResourcePoolCredential> findByUsername(String username);
    void delete(String id);
    void update(ResourcePoolCredential credential);
}
