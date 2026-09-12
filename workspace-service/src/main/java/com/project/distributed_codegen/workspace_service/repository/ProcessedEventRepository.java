package com.project.distributed_codegen.workspace_service.repository;

import com.project.distributed_codegen.workspace_service.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
