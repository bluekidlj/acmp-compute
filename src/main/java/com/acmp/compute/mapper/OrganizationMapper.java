package com.acmp.compute.mapper;

import com.acmp.compute.entity.Organization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface OrganizationMapper {

    int insert(Organization entity);

    Optional<Organization> findById(@Param("id") String id);
}
