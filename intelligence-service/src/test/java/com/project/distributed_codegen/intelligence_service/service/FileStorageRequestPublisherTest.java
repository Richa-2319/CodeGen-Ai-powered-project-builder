package com.project.distributed_codegen.intelligence_service.service;

import com.project.distributed_codegen.common_lib.enums.ChatEventStatus;
import com.project.distributed_codegen.common_lib.enums.ChatEventType;
import com.project.distributed_codegen.common_lib.event.FileStoreRequestEvent;
import com.project.distributed_codegen.intelligence_service.entity.ChatEvent;
import com.project.distributed_codegen.intelligence_service.repository.ChatEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FileStorageRequestPublisherTest {

    private final ChatEventRepository events = mock(ChatEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final FileStorageRequestPublisher publisher = new FileStorageRequestPublisher(events, kafka);

    @Test
    void persistsSagaIdsBeforeSendingSoImmediateRepliesCanFindThem() {
        ChatEvent event = fileEvent();
        when(kafka.send(eq("file-storage-request-event"), eq("project-7"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.persistAndPublish(List.of(event), 7L, 2L);

        var order = inOrder(events, kafka);
        order.verify(events).saveAll(List.of(event));
        ArgumentCaptor<FileStoreRequestEvent> request = ArgumentCaptor.forClass(FileStoreRequestEvent.class);
        order.verify(kafka).send(eq("file-storage-request-event"), eq("project-7"), request.capture());
        assertThat(event.getSagaId()).isNotBlank();
        assertThat(request.getValue().sagaId()).isEqualTo(event.getSagaId());
        assertThat(request.getValue().userId()).isEqualTo(2L);
    }

    @Test
    void doesNotSendWhenDatabasePersistenceFails() {
        when(events.saveAll(any())).thenThrow(new IllegalStateException("test database failure"));

        assertThatThrownBy(() -> publisher.persistAndPublish(List.of(fileEvent()), 7L, 2L))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(kafka);
    }

    @Test
    void recordsAnAsynchronousKafkaFailureAgainstThePendingSaga() {
        ChatEvent event = fileEvent();
        when(kafka.send(eq("file-storage-request-event"), eq("project-7"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("test send failure")));

        publisher.persistAndPublish(List.of(event), 7L, 2L);

        verify(events).markPendingSagaFailed(event.getSagaId());
    }

    @Test
    void recordsASynchronousKafkaSerializationFailure() {
        ChatEvent event = fileEvent();
        when(kafka.send(eq("file-storage-request-event"), eq("project-7"), any()))
                .thenThrow(new IllegalArgumentException("test serialization failure"));

        publisher.persistAndPublish(List.of(event), 7L, 2L);

        verify(events).markPendingSagaFailed(event.getSagaId());
    }

    private ChatEvent fileEvent() {
        return ChatEvent.builder().type(ChatEventType.FILE_EDIT).status(ChatEventStatus.PENDING)
                .filePath("src/App.tsx").content("source").build();
    }
}
