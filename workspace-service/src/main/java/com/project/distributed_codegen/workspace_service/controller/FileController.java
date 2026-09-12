package com.project.distributed_codegen.workspace_service.controller;


import com.project.distributed_codegen.common_lib.dto.FileTreeDto;
import com.project.distributed_codegen.workspace_service.dto.project.FileContentResponse;
import com.project.distributed_codegen.workspace_service.service.ProjectFileService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import com.project.distributed_codegen.workspace_service.dto.project.FileWriteRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/files")
public class FileController {

    private final ProjectFileService projectFileService;

    @GetMapping
    @PreAuthorize("@security.canViewProject(#projectId)")
    public ResponseEntity<FileTreeDto> getFileTree(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectFileService.getFileTree(projectId));
    }

    @GetMapping("/content")
    @PreAuthorize("@security.canViewProject(#projectId)")
    public ResponseEntity<FileContentResponse> getFile(
            @PathVariable Long projectId,
            @RequestParam String path) {
        return ResponseEntity.ok(new FileContentResponse(
                path,
                projectFileService.getFileContent(projectId, path)));
    }

    @PutMapping("/content")
    @PreAuthorize("@security.canEditProject(#projectId)")
    public ResponseEntity<FileContentResponse> saveFile(@PathVariable Long projectId,
            @RequestBody @Valid FileWriteRequest request) {
        projectFileService.saveFile(projectId, request.path(), request.content());
        return ResponseEntity.ok(new FileContentResponse(request.path(), request.content()));
    }

    @GetMapping(value = "/download-zip", produces = "application/zip")
    @PreAuthorize("@security.canViewProject(#projectId)")
    public ResponseEntity<StreamingResponseBody> downloadProject(@PathVariable Long projectId) {
        StreamingResponseBody body = outputStream -> projectFileService.writeProjectZip(projectId, outputStream);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=project-" + projectId + ".zip")
                .body(body);
    }

}
