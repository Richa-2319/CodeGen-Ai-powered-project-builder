package com.project.distributed_codegen.workspace_service.dto.project;

import java.time.Instant;
import java.util.UUID;

public record PublicationResponse(UUID slug, boolean active, Instant publishedAt) {}
