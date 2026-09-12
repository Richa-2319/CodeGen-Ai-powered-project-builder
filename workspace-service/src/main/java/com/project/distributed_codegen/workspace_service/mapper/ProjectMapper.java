package com.project.distributed_codegen.workspace_service.mapper;

import com.project.distributed_codegen.common_lib.enums.ProjectRole;
import com.project.distributed_codegen.workspace_service.dto.project.ProjectResponse;
import com.project.distributed_codegen.workspace_service.dto.project.ProjectSummaryResponse;
import com.project.distributed_codegen.workspace_service.entity.Project;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    ProjectResponse toProjectResponse(Project project, ProjectRole role);

    ProjectSummaryResponse toProjectSummaryResponse(Project project, ProjectRole role);

    List<ProjectSummaryResponse> toListOfProjectSummaryResponse(List<Project> projects);

}
