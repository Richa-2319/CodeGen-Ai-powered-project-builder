package com.project.distributed_codegen.workspace_service.service;

import com.project.distributed_codegen.common_lib.error.BadRequestException;
import com.project.distributed_codegen.common_lib.error.ResourceNotFoundException;
import com.project.distributed_codegen.workspace_service.dto.project.PreviewBundle;
import com.project.distributed_codegen.workspace_service.dto.project.PublicationResponse;
import com.project.distributed_codegen.workspace_service.entity.ProjectPublication;
import com.project.distributed_codegen.workspace_service.repository.ProjectPublicationRepository;
import com.project.distributed_codegen.workspace_service.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicationService {
    private final ProjectPublicationRepository publications;
    private final ProjectRepository projects;
    private final PreviewSnapshotService previews;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public PublicationResponse status(Long projectId) {
        requireProject(projectId);
        return publications.findById(projectId).map(this::response)
                .orElseGet(() -> new PublicationResponse(null, false, null));
    }

    @Transactional
    public PublicationResponse publish(Long projectId) {
        var project = projects.lockActiveProject(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));
        var bundle = previews.snapshot(projectId);
        if (!bundle.files().containsKey("index.html") && bundle.files().keySet().stream().noneMatch(path ->
                path.matches("(^|.*/)(main|index|App)\\.(tsx?|jsx?)$"))) {
            throw new BadRequestException("Generate an app and verify its preview before publishing");
        }
        var publication = publications.findById(projectId).orElseGet(() -> {
            var created = new ProjectPublication(); created.setProject(project); created.setSlug(UUID.randomUUID()); return created;
        });
        publication.setSnapshot(mapper.writeValueAsString(bundle));
        publication.setActive(true);
        publication.setPublishedAt(Instant.now());
        return response(publications.save(publication));
    }

    @Transactional
    public void unpublish(Long projectId) {
        requireProject(projectId);
        publications.findById(projectId).ifPresent(publication -> publication.setActive(false));
    }

    @Transactional(readOnly = true)
    public PreviewBundle publicApp(UUID slug) {
        var publication = publications.findBySlugAndActiveTrueAndProjectDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Published app", slug.toString()));
        return mapper.readValue(publication.getSnapshot(), PreviewBundle.class);
    }

    private void requireProject(Long id) {
        projects.findById(id).filter(project -> project.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id.toString()));
    }

    private PublicationResponse response(ProjectPublication publication) {
        return new PublicationResponse(publication.getSlug(), publication.isActive(), publication.getPublishedAt());
    }
}
