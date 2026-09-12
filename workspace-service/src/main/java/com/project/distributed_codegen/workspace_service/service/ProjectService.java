package com.project.distributed_codegen.workspace_service.service;


import com.project.distributed_codegen.common_lib.enums.ProjectPermission;
import com.project.distributed_codegen.workspace_service.dto.project.ProjectRequest;
import com.project.distributed_codegen.workspace_service.dto.project.ProjectResponse;
import com.project.distributed_codegen.workspace_service.dto.project.ProjectSummaryResponse;

import java.util.List;

public interface ProjectService {
    List<ProjectSummaryResponse> getUserProjects();

    ProjectSummaryResponse getUserProjectById(Long id);

    ProjectResponse createProject(ProjectRequest request);

    ProjectResponse updateProject(Long id, ProjectRequest request);

    void softDelete(Long id);

    boolean hasPermission(Long projectId, ProjectPermission permission);
}
