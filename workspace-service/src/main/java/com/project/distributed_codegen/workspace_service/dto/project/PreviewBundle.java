package com.project.distributed_codegen.workspace_service.dto.project;

import java.util.Map;

public record PreviewBundle(String name, Map<String, String> files) {}
