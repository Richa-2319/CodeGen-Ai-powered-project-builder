package com.project.distributed_codegen.workspace_service.controller;

import com.project.distributed_codegen.workspace_service.dto.project.PreviewBundle;
import com.project.distributed_codegen.workspace_service.dto.project.PublicationResponse;
import com.project.distributed_codegen.workspace_service.service.PublicationService;
import com.project.distributed_codegen.workspace_service.service.PreviewSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PublicationController {
    private final PublicationService publications;
    private final PreviewSnapshotService previews;

    @GetMapping("/projects/{projectId}/preview")
    @PreAuthorize("@security.canViewProject(#projectId)")
    public ResponseEntity<PreviewBundle> preview(@PathVariable Long projectId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(previews.snapshot(projectId));
    }

    @GetMapping("/projects/{projectId}/publication")
    @PreAuthorize("@security.canViewProject(#projectId)")
    public PublicationResponse status(@PathVariable Long projectId) { return publications.status(projectId); }

    @PostMapping("/projects/{projectId}/publication")
    @PreAuthorize("@security.canManageMembers(#projectId)")
    public PublicationResponse publish(@PathVariable Long projectId) { return publications.publish(projectId); }

    @DeleteMapping("/projects/{projectId}/publication")
    @PreAuthorize("@security.canManageMembers(#projectId)")
    public ResponseEntity<Void> unpublish(@PathVariable Long projectId) {
        publications.unpublish(projectId); return ResponseEntity.noContent().build();
    }

    @GetMapping("/public/apps/{slug}")
    public ResponseEntity<PreviewBundle> publicApp(@PathVariable UUID slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(publications.publicApp(slug));
    }
}
