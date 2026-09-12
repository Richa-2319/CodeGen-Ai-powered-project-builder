package com.project.distributed_codegen.workspace_service.consumer;

import com.project.distributed_codegen.common_lib.event.FileStoreRequestEvent;
import com.project.distributed_codegen.common_lib.event.FileStoreResponseEvent;
import com.project.distributed_codegen.workspace_service.repository.ProcessedEventRepository;
import com.project.distributed_codegen.workspace_service.service.ProjectFileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FileStorageConsumerTest {
    private final ProjectFileService files = mock(ProjectFileService.class);
    private final ProcessedEventRepository processedEvents = mock(ProcessedEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final FileStorageConsumer consumer = new FileStorageConsumer(files, processedEvents, kafka);
    private final FileStoreRequestEvent request = new FileStoreRequestEvent(7L, "test-saga", "src/App.tsx", "source", 2L);

    @Test
    void acknowledgesOnlyAfterSavingTheFileAndItsIdempotencyMarker() {
        when(kafka.send(eq("file-store-responses"), eq("project-7"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        consumer.consumeFileEvent(request);

        var order = inOrder(files, processedEvents, kafka);
        order.verify(processedEvents).existsById("test-saga");
        order.verify(files).saveFile(7L, "src/App.tsx", "source");
        order.verify(processedEvents).save(any());
        order.verify(kafka).send(eq("file-store-responses"), eq("project-7"), any());
    }

    @Test
    void propagatesFailedAcknowledgementSoTheKafkaListenerCanRetry() {
        when(kafka.send(eq("file-store-responses"), eq("project-7"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("test broker failure")));

        assertThatThrownBy(() -> consumer.consumeFileEvent(request))
                .isInstanceOf(IllegalStateException.class).hasMessage("Failed to acknowledge file storage");
        verify(kafka, times(1)).send(eq("file-store-responses"), eq("project-7"), any());
    }

    @Test
    void resendsAcknowledgementWithoutRewritingAnAlreadyStoredFile() {
        when(processedEvents.existsById("test-saga")).thenReturn(true);
        when(kafka.send(eq("file-store-responses"), eq("project-7"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        consumer.consumeFileEvent(request);

        verifyNoInteractions(files);
        verify(kafka).send(eq("file-store-responses"), eq("project-7"), any());
    }

    @Test
    void returnsFailedStatusWithoutMarkingAnUnstoredFileAsProcessed() {
        doThrow(new IllegalStateException("test storage failure"))
                .when(files).saveFile(7L, "src/App.tsx", "source");
        when(kafka.send(eq("file-store-responses"), eq("project-7"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        consumer.consumeFileEvent(request);

        verify(processedEvents, never()).save(any());
        ArgumentCaptor<FileStoreResponseEvent> response = ArgumentCaptor.forClass(FileStoreResponseEvent.class);
        verify(kafka).send(eq("file-store-responses"), eq("project-7"), response.capture());
        assertThat(response.getValue().success()).isFalse();
    }
}
