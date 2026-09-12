package com.project.distributed_codegen.workspace_service.service;

import com.project.distributed_codegen.common_lib.error.BadRequestException;
import com.project.distributed_codegen.common_lib.error.ResourceNotFoundException;
import com.project.distributed_codegen.workspace_service.dto.project.PreviewBundle;
import com.project.distributed_codegen.workspace_service.repository.ProjectFileRepository;
import com.project.distributed_codegen.workspace_service.repository.ProjectRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PreviewSnapshotService {
    static final int MAX_FILES = 200;
    static final int MAX_FILE_BYTES = 1_048_576;
    static final int MAX_TOTAL_BYTES = 5_242_880;
    private final ProjectRepository projects;
    private final ProjectFileRepository files;
    private final MinioClient minio;
    @Value("${minio.project-bucket}")
    private String bucket;

    public PreviewBundle snapshot(Long projectId) {
        var project = projects.findById(projectId).filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));
        Map<String, String> content = new LinkedHashMap<>();
        int total = 0;
        var frontendFiles = files.findByProjectId(projectId).stream().filter(file -> isPreviewFile(file.getPath())).toList();
        if (frontendFiles.size() > MAX_FILES) throw new BadRequestException("Preview supports up to 200 frontend files");
        for (var file : frontendFiles) {
            try (var stream = minio.getObject(GetObjectArgs.builder().bucket(bucket).object(file.getMinioObjectKey()).build())) {
                byte[] bytes = stream.readNBytes(MAX_FILE_BYTES + 1);
                total += bytes.length;
                if (bytes.length > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES) {
                    throw new BadRequestException("Preview supports files up to 1 MB and 5 MB total");
                }
                content.put(file.getPath(), new String(bytes, StandardCharsets.UTF_8));
            } catch (BadRequestException error) {
                throw error;
            } catch (Exception error) {
                throw new IllegalStateException("Could not read the saved preview files; try again after generation completes");
            }
        }
        return new PreviewBundle(project.getName(), Map.copyOf(content));
    }

    public static boolean isPreviewFile(String path) {
        if (path == null || path.contains("\\") || path.startsWith("/") || path.length() > 1024) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        for (String part : lower.split("/")) {
            if (part.startsWith(".") || part.equals("node_modules") || part.equals("dist") || part.equals("target")) return false;
        }
        // Browser source only: no environment, credential, build or server config.
        return lower.equals("index.html") || lower.equals("package.json")
                || ((lower.startsWith("src/") || lower.startsWith("public/") || !lower.contains("/"))
                    && !lower.contains("config.")
                    && lower.matches(".*\\.(tsx?|jsx?|css|json|svg|html)$"));
    }
}
