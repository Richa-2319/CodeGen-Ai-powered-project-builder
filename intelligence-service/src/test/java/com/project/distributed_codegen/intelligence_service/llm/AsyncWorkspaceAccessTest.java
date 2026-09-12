package com.project.distributed_codegen.intelligence_service.llm;

import com.project.distributed_codegen.common_lib.dto.FileTreeDto;
import com.project.distributed_codegen.intelligence_service.client.WorkspaceClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncWorkspaceAccessTest {

    private final WorkspaceClient workspace = mock(WorkspaceClient.class);
    private final String authorizationHeader = "Bearer test-only-captured-identity";

    @Test
    void fileReadToolForwardsCapturedIdentityFromAWorkerThread() throws Exception {
        when(workspace.getFileContent(7L, "src/App.tsx", authorizationHeader)).thenReturn("source");
        CodeGenerationTools tools = new CodeGenerationTools(7L, workspace, authorizationHeader);

        List<String> files = CompletableFuture.supplyAsync(() -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            return tools.readFiles(List.of("/src/App.tsx"));
        }).get(5, TimeUnit.SECONDS);

        assertThat(files).singleElement().asString().contains("source");
        verify(workspace).getFileContent(7L, "src/App.tsx", authorizationHeader);
    }

    @Test
    void fileTreeAdvisorForwardsCapturedIdentityFromAWorkerThread() throws Exception {
        when(workspace.getFileTree(7L, authorizationHeader)).thenReturn(new FileTreeDto(List.of()));
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any())).thenReturn(Flux.empty());
        FileTreeContextAdvisor advisor = new FileTreeContextAdvisor(workspace, authorizationHeader);
        ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt("generate"))
                .context(Map.of("projectId", 7L)).build();

        CompletableFuture.runAsync(() -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            advisor.adviseStream(request, chain).blockLast();
        }).get(5, TimeUnit.SECONDS);

        verify(workspace).getFileTree(7L, authorizationHeader);
        verify(chain).nextStream(any());
    }
}
