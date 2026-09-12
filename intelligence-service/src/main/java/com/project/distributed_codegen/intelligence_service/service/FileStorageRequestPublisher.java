package com.project.distributed_codegen.intelligence_service.service;

import com.project.distributed_codegen.common_lib.enums.ChatEventType;
import com.project.distributed_codegen.common_lib.event.FileStoreRequestEvent;
import com.project.distributed_codegen.intelligence_service.entity.ChatEvent;
import com.project.distributed_codegen.intelligence_service.repository.ChatEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageRequestPublisher {

    private final ChatEventRepository chatEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // saveAll must commit before Kafka can deliver a response for the new saga IDs.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void persistAndPublish(List<ChatEvent> events, Long projectId, Long userId) {
        List<ChatEvent> fileEvents = events.stream()
                .filter(event -> event.getType() == ChatEventType.FILE_EDIT)
                .toList();
        fileEvents.forEach(event -> event.setSagaId(UUID.randomUUID().toString()));
        chatEventRepository.saveAll(events);

        for (ChatEvent event : fileEvents) {
            FileStoreRequestEvent request = new FileStoreRequestEvent(
                    projectId, event.getSagaId(), event.getFilePath(), event.getContent(), userId);
            try {
                kafkaTemplate.send("file-storage-request-event", "project-" + projectId, request)
                        .whenComplete((result, error) -> {
                            if (error != null) {
                                recordSendFailure(event.getSagaId());
                            }
                        });
            } catch (RuntimeException exception) {
                recordSendFailure(event.getSagaId());
            }
        }
    }

    private void recordSendFailure(String sagaId) {
        log.error("Failed to publish file storage request for saga {}", sagaId);
        chatEventRepository.markPendingSagaFailed(sagaId);
    }
}
