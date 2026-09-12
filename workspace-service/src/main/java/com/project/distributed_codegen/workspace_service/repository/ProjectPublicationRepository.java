package com.project.distributed_codegen.workspace_service.repository;

import com.project.distributed_codegen.workspace_service.entity.ProjectPublication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ProjectPublicationRepository extends JpaRepository<ProjectPublication, Long> {
    Optional<ProjectPublication> findBySlugAndActiveTrueAndProjectDeletedAtIsNull(UUID slug);
}
