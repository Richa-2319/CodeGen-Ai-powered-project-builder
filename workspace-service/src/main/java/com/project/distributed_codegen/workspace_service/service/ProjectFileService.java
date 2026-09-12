package com.project.distributed_codegen.workspace_service.service;


import com.project.distributed_codegen.common_lib.dto.FileTreeDto;

import java.io.OutputStream;

public interface ProjectFileService {
    FileTreeDto getFileTree(Long projectId);

    String getFileContent(Long projectId, String path);

    void saveFile(Long projectId, String filePath, String fileContent);

    void writeProjectZip(Long projectId, OutputStream outputStream);
}
