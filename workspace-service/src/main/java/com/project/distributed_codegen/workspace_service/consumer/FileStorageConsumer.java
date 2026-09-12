package com.project.distributed_codegen.workspace_service.consumer;

import com.project.distributed_codegen.common_lib.event.FileStoreRequestEvent;
import com.project.distributed_codegen.common_lib.event.FileStoreResponseEvent;
import com.project.distributed_codegen.workspace_service.entity.ProcessedEvent;
import com.project.distributed_codegen.workspace_service.repository.ProcessedEventRepository;
import com.project.distributed_codegen.workspace_service.service.ProjectFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileStorageConsumer {

    private final ProjectFileService projectFileService;
    private final ProcessedEventRepository processedEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "file-storage-request-event", groupId = "workspace-group")
    public void consumeFileEvent(FileStoreRequestEvent requestEvent) {

        // Idempotency check
        if (processedEventRepository.existsById(requestEvent.sagaId())) {
            log.info("Duplicate Saga detected: {}. Resending previous ACK.", requestEvent.sagaId());
            sendResponse(requestEvent, true, null);
            return;
        }

        try {
            log.info("Saving file: {}", requestEvent.filePath());

            projectFileService.saveFile(requestEvent.projectId(), requestEvent.filePath(), requestEvent.content());
            processedEventRepository.save(new ProcessedEvent(
                    requestEvent.sagaId(), LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Error saving file for saga {}", requestEvent.sagaId());
            sendResponse(requestEvent, false, "Failed to store project file");
            return;
        }

        // File metadata and the idempotency marker have committed before acknowledging.
        sendResponse(requestEvent, true, null);
    }

    private void sendResponse(FileStoreRequestEvent req, boolean success, String error) {
        FileStoreResponseEvent response = FileStoreResponseEvent.builder()
                .sagaId(req.sagaId())
                .projectId(req.projectId())
                .success(success)
                .errorMessage(error)
                .build();
        try {
            // Surface a failed send to the listener so Kafka can retry the request.
            kafkaTemplate.send("file-store-responses", "project-" + req.projectId(), response)
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while acknowledging file storage", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Failed to acknowledge file storage", exception);
        }
    }
}
