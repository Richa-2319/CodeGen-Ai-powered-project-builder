package com.project.distributed_codegen.workspace_service.service;

import com.project.distributed_codegen.common_lib.error.BadRequestException;
import com.project.distributed_codegen.common_lib.error.ResourceNotFoundException;
import com.project.distributed_codegen.workspace_service.dto.project.PreviewBundle;
import com.project.distributed_codegen.workspace_service.entity.Project;
import com.project.distributed_codegen.workspace_service.entity.ProjectPublication;
import com.project.distributed_codegen.workspace_service.repository.ProjectPublicationRepository;
import com.project.distributed_codegen.workspace_service.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PublicationServiceTest {
    private final ProjectPublicationRepository publications = mock(ProjectPublicationRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final PreviewSnapshotService previews = mock(PreviewSnapshotService.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final PublicationService service = new PublicationService(publications, projects, previews, mapper);

    @Test
    void publicationKeepsSnapshotAndStableLinkAcrossRepublish() {
        Project project = Project.builder().id(7L).name("Tasks").build();
        when(projects.lockActiveProject(7L)).thenReturn(Optional.of(project));
        when(projects.findById(7L)).thenReturn(Optional.of(project));
        var publication = new ProjectPublication(); publication.setProject(project); publication.setSlug(UUID.randomUUID());
        when(publications.findById(7L)).thenReturn(Optional.of(publication));
        when(publications.save(any())).thenAnswer(call -> call.getArgument(0));
        when(previews.snapshot(7L)).thenReturn(new PreviewBundle("Tasks", Map.of("src/App.tsx", "version one")));
        var first = service.publish(7L);
        when(publications.findBySlugAndActiveTrueAndProjectDeletedAtIsNull(first.slug())).thenReturn(Optional.of(publication));
        when(previews.snapshot(7L)).thenReturn(new PreviewBundle("Tasks", Map.of("src/App.tsx", "version two")));
        assertEquals("version one", service.publicApp(first.slug()).files().get("src/App.tsx"));
        var second = service.publish(7L);
        assertEquals(first.slug(), second.slug());
        assertEquals("version two", service.publicApp(first.slug()).files().get("src/App.tsx"));
        service.unpublish(7L);
        assertFalse(publication.isActive());
    }

    @Test
    void emptyProjectsCannotBePublished() {
        when(projects.lockActiveProject(7L)).thenReturn(Optional.of(Project.builder().id(7L).build()));
        when(previews.snapshot(7L)).thenReturn(new PreviewBundle("Empty", Map.of("README.md", "hello")));
        assertThrows(BadRequestException.class, () -> service.publish(7L));
        verify(publications, never()).save(any());
    }

    @Test
    void missingOrInactivePublicationsHaveNoPublicContent() {
        UUID slug = UUID.randomUUID();
        when(publications.findBySlugAndActiveTrueAndProjectDeletedAtIsNull(slug)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.publicApp(slug));
        verifyNoInteractions(previews);
    }

    @Test
    void snapshotExcludesEnvironmentCredentialsAndBuildOutputs() {
        for (String path : new String[]{".env", "src/.env.local", "src/../secret.json", "src/node_modules/a.js", "private.key", "vite.config.ts", "/src/App.tsx", "src\\App.tsx"}) {
            assertFalse(PreviewSnapshotService.isPreviewFile(path), path);
        }
        for (String path : new String[]{"src/App.tsx", "src/data.json", "src/style.css", "public/icon.svg", "index.html"}) {
            assertTrue(PreviewSnapshotService.isPreviewFile(path), path);
        }
    }
}
