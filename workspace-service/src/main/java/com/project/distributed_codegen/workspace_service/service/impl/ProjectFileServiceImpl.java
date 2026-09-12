package com.project.distributed_codegen.workspace_service.service.impl;

import com.project.distributed_codegen.common_lib.dto.FileNode;
import com.project.distributed_codegen.common_lib.dto.FileTreeDto;
import com.project.distributed_codegen.common_lib.error.BadRequestException;
import com.project.distributed_codegen.common_lib.error.ResourceNotFoundException;
import com.project.distributed_codegen.workspace_service.entity.Project;
import com.project.distributed_codegen.workspace_service.entity.ProjectFile;
import com.project.distributed_codegen.workspace_service.mapper.ProjectFileMapper;
import com.project.distributed_codegen.workspace_service.repository.ProjectFileRepository;
import com.project.distributed_codegen.workspace_service.repository.ProjectRepository;
import com.project.distributed_codegen.workspace_service.service.ProjectFileService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectFileServiceImpl implements ProjectFileService {

    private final ProjectRepository projectRepository;
    private final ProjectFileRepository projectFileRepository;
    private final MinioClient minioClient;
    private final ProjectFileMapper projectFileMapper;

    @Value("${minio.project-bucket}")
    private String projectBucket;

    @Value("${app.download.max-files:2000}")
    private int maxDownloadFiles;

    @Value("${app.download.max-uncompressed-bytes:52428800}")
    private long maxDownloadBytes;


    @Override
    public FileTreeDto getFileTree(Long projectId) {
        List<ProjectFile> projectFileList = projectFileRepository.findByProjectId(projectId);
        List<FileNode> projectFileNodes = projectFileMapper.toListOfFileNode(projectFileList);
        return new FileTreeDto(projectFileNodes);
    }

    @Override
    public String getFileContent(Long projectId, String path) {
        String cleanPath = normalizeProjectPath(path);
        ProjectFile projectFile = projectFileRepository.findByProjectIdAndPath(projectId, cleanPath)
                .orElseThrow(() -> new ResourceNotFoundException("Project file", cleanPath));

        try (
                InputStream is = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(projectBucket)
                                .object(projectFile.getMinioObjectKey())
                                .build())) {

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to read file: {}/{}", projectId, cleanPath, e);
            throw new RuntimeException("Failed to read file content", e);
        }
    }

    @Override
    @Transactional
    public void saveFile(Long projectId, String path, String content) {
        Project project = projectRepository.findById(projectId).orElseThrow(
                () -> new ResourceNotFoundException("Project", projectId.toString())
        );

        String cleanPath = normalizeProjectPath(path);
        String objectKey = projectId + "/" + cleanPath;

        try {
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            InputStream inputStream = new ByteArrayInputStream(contentBytes);
            // saving the file content
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(projectBucket)
                            .object(objectKey)
                            .stream(inputStream, contentBytes.length, -1)
                            .contentType(determineContentType(cleanPath))
                            .build());

            // Saving the metaData
            ProjectFile file = projectFileRepository.findByProjectIdAndPath(projectId, cleanPath)
                    .orElseGet(() -> ProjectFile.builder()
                            .project(project)
                            .path(cleanPath)
                            .minioObjectKey(objectKey) // Use the key we generated
                            .createdAt(Instant.now())
                            .build());

            file.setUpdatedAt(Instant.now());
            projectFileRepository.save(file);
            log.info("Saved file: {}", objectKey);
        } catch (Exception e) {
            log.error("Failed to save file {}/{}", projectId, cleanPath, e);
            throw new RuntimeException("File save failed", e);
        }

    }

    @Override
    public void writeProjectZip(Long projectId, OutputStream outputStream) {
        projectRepository.findById(projectId).orElseThrow(
                () -> new ResourceNotFoundException("Project", projectId.toString()));

        List<ProjectFile> files = projectFileRepository.findByProjectId(projectId);
        if (files.size() > maxDownloadFiles) {
            throw new BadRequestException("Project contains too many files to download");
        }

        Set<String> archivePaths = new HashSet<>();
        long totalBytes = 0;
        byte[] buffer = new byte[8192];
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8);

        try {
            for (ProjectFile file : files) {
                String archivePath = normalizeProjectPath(file.getPath());
                if (!archivePaths.add(archivePath)) {
                    throw new BadRequestException("Project contains duplicate file paths");
                }

                zipOutputStream.putNextEntry(new ZipEntry(archivePath));
                try (InputStream inputStream = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(projectBucket)
                                .object(file.getMinioObjectKey())
                                .build())) {
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        totalBytes += bytesRead;
                        if (totalBytes > maxDownloadBytes) {
                            throw new BadRequestException("Project is too large to download");
                        }
                        zipOutputStream.write(buffer, 0, bytesRead);
                    }
                }
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create project archive: {}", projectId, e);
            throw new RuntimeException("Project download failed", e);
        }
    }

    private String normalizeProjectPath(String path) {
        if (path == null || path.isBlank() || path.indexOf('\0') >= 0) {
            throw new BadRequestException("Invalid project file path");
        }

        String slashPath = path.replace('\\', '/');
        while (slashPath.startsWith("/")) {
            slashPath = slashPath.substring(1);
        }

        try {
            Path normalized = Path.of(slashPath).normalize();
            String cleanPath = normalized.toString().replace('\\', '/');
            if (cleanPath.isBlank()
                    || cleanPath.equals(".")
                    || cleanPath.equals("..")
                    || cleanPath.startsWith("../")
                    || cleanPath.matches("^[A-Za-z]:.*")) {
                throw new BadRequestException("Invalid project file path");
            }
            return cleanPath;
        } catch (InvalidPathException e) {
            throw new BadRequestException("Invalid project file path");
        }
    }

    private String determineContentType(String path) {
        String type = URLConnection.guessContentTypeFromName(path);
        if (type != null) return type;
        if (path.endsWith(".jsx") || path.endsWith(".ts") || path.endsWith(".tsx")) return "text/javascript";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".css")) return "text/css";

        return "text/plain";
    }
}
