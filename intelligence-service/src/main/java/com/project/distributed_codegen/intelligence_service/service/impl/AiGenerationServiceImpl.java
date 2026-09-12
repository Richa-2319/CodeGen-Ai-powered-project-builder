package com.project.distributed_codegen.intelligence_service.service.impl;

import com.project.distributed_codegen.common_lib.enums.ChatEventStatus;
import com.project.distributed_codegen.common_lib.enums.ChatEventType;
import com.project.distributed_codegen.common_lib.enums.MessageRole;
import com.project.distributed_codegen.common_lib.security.AuthUtil;
import com.project.distributed_codegen.intelligence_service.client.WorkspaceClient;
import com.project.distributed_codegen.intelligence_service.dto.chat.StreamResponse;
import com.project.distributed_codegen.intelligence_service.entity.ChatEvent;
import com.project.distributed_codegen.intelligence_service.entity.ChatMessage;
import com.project.distributed_codegen.intelligence_service.entity.ChatSession;
import com.project.distributed_codegen.intelligence_service.entity.ChatSessionId;
import com.project.distributed_codegen.intelligence_service.llm.CodeGenerationTools;
import com.project.distributed_codegen.intelligence_service.llm.FileTreeContextAdvisor;
import com.project.distributed_codegen.intelligence_service.llm.LlmResponseParser;
import com.project.distributed_codegen.intelligence_service.llm.PromptUtils;
import com.project.distributed_codegen.intelligence_service.service.FileStorageRequestPublisher;
import com.project.distributed_codegen.intelligence_service.repository.ChatMessageRepository;
import com.project.distributed_codegen.intelligence_service.repository.ChatSessionRepository;
import com.project.distributed_codegen.intelligence_service.service.AiGenerationService;
import com.project.distributed_codegen.intelligence_service.service.UsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiGenerationServiceImpl implements AiGenerationService {

    private final ChatClient chatClient;
    private final AuthUtil authUtil;
    private final ChatSessionRepository chatSessionRepository;
    private final LlmResponseParser llmResponseParser;
    private final ChatMessageRepository chatMessageRepository;
    private final UsageService usageService;
    private final WorkspaceClient workspaceClient;
    private final FileStorageRequestPublisher fileStorageRequestPublisher;


    @Override
    @PreAuthorize("@security.canEditProject(#projectId)")
    public Flux<StreamResponse> streamResponse(String userMessage, Long projectId) {

        usageService.checkDailyTokensUsage();

        Long userId = authUtil.getCurrentUserId();
        String authorizationHeader = authUtil.getCurrentAuthorizationHeader();
        ChatSession chatSession = createChatSessionIfNotExists(projectId, userId);

        Map<String, Object> advisorParams = Map.of(
                "userId", userId,
                "projectId", projectId
        );

        StringBuilder fullResponseBuffer = new StringBuilder();
        // The AI stream and tools execute outside the servlet's security-context thread.
        CodeGenerationTools codeGenerationTools = new CodeGenerationTools(projectId, workspaceClient, authorizationHeader);
        FileTreeContextAdvisor fileTreeContextAdvisor = new FileTreeContextAdvisor(workspaceClient, authorizationHeader);

        AtomicReference<Long> startTime = new AtomicReference<>(System.currentTimeMillis());
        AtomicReference<Long> endTime = new AtomicReference<>(0L);
        AtomicReference<Usage> usageRef = new AtomicReference<>();

        return chatClient.prompt()
                .system(PromptUtils.CODE_GENERATION_SYSTEM_PROMPT)
                .user(userMessage)
                .tools(codeGenerationTools)
                .advisors(advisorSpec -> {
                            advisorSpec.params(advisorParams);
                            advisorSpec.advisors(fileTreeContextAdvisor);
                        }
                )
                .stream()
                .chatResponse()
                .doOnNext(response -> {
                    Usage reportedUsage = response.getMetadata().getUsage();
                    // Providers may send usage in a final chunk with no choices.
                    if (reportedUsage != null && (positive(reportedUsage.getTotalTokens()) > 0
                            || positive(reportedUsage.getPromptTokens()) > 0
                            || positive(reportedUsage.getCompletionTokens()) > 0)) {
                        usageRef.set(reportedUsage);
                    }
                    if (response.getResults() != null && !response.getResults().isEmpty()) {
                        String content = response.getResult().getOutput().getText();

                        if(content != null && !content.isEmpty() && endTime.get() == 0) { // first non-empty chunk received
                            endTime.set(System.currentTimeMillis());
                        }
                        if (content != null) {
                            fullResponseBuffer.append(content);
                        }
                    }

                })
                .doOnComplete(() -> {
                    Schedulers.boundedElastic().schedule(() -> {
                        try {
                            long firstTokenAt = endTime.get();
                            long duration = firstTokenAt == 0
                                    ? 0
                                    : Math.max(0, (firstTokenAt - startTime.get()) / 1000);
                            finalizeChats(
                                    userMessage,
                                    chatSession,
                                    fullResponseBuffer.toString(),
                                    duration,
                                    usageRef.get(),
                                    userId);
                        } catch (RuntimeException exception) {
                            log.error("Failed to persist completed chat for projectId: {}", projectId, exception);
                        }
                    });
                })
                .doOnError(error -> log.error("Error during streaming for projectId: {}", projectId, error))
                .map(response -> {
                    if (response.getResults() != null && !response.getResults().isEmpty()) {
                        String text = response.getResult().getOutput().getText();
                        return new StreamResponse(text != null ? text : "");
                    }
                    return new StreamResponse("");
                });
    }

    private void finalizeChats(String userMessage, ChatSession chatSession, String fullText, Long duration, Usage usage, Long userId) {
        Long projectId = chatSession.getId().getProjectId();

        TokenCounts counts = tokenCounts(usage, userMessage, fullText);
        int promptTokens = counts.prompt();
        int completionTokens = counts.completion();
        int totalTokens = counts.total();
        if (totalTokens > 0) {
            usageService.recordTokenUsage(chatSession.getId().getUserId(), totalTokens);
        }

        // Save the User message
        chatMessageRepository.save(
                ChatMessage.builder()
                        .chatSession(chatSession)
                        .role(MessageRole.USER)
                        .content(userMessage)
                        .tokensUsed(promptTokens)
                        .build()
        );

        ChatMessage assistantChatMessage = ChatMessage.builder()
                .role(MessageRole.ASSISTANT)
                .content("Assistant Message here...")
                .chatSession(chatSession)
                .tokensUsed(completionTokens)
                .build();

        assistantChatMessage = chatMessageRepository.save(assistantChatMessage);

        List<ChatEvent> chatEventList = llmResponseParser.parseChatEvents(fullText, assistantChatMessage);
        chatEventList.addFirst(ChatEvent.builder()
                .type(ChatEventType.THOUGHT)
                .status(ChatEventStatus.CONFIRMED)
                .chatMessage(assistantChatMessage)
                .content("Thought for "+duration+"s")
                .sequenceOrder(0)
                .build());

        fileStorageRequestPublisher.persistAndPublish(chatEventList, projectId, userId);
    }

    record TokenCounts(int prompt, int completion, int total) {}

    static TokenCounts tokenCounts(Usage usage, String prompt, String completion) {
        int promptTokens = usage == null ? 0 : positive(usage.getPromptTokens());
        int completionTokens = usage == null ? 0 : positive(usage.getCompletionTokens());
        int totalTokens = usage == null ? 0 : positive(usage.getTotalTokens());
        if (promptTokens == 0) promptTokens = estimateTokens(prompt);
        if (completionTokens == 0) completionTokens = estimateTokens(completion);
        if (totalTokens == 0) totalTokens = promptTokens + completionTokens;
        return new TokenCounts(promptTokens, completionTokens, totalTokens);
    }

    private static int positive(Integer tokens) {
        return tokens == null ? 0 : Math.max(0, tokens);
    }

    private static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int utf8Bytes = text.getBytes(StandardCharsets.UTF_8).length;
        return Math.max(1, (utf8Bytes + 2) / 3);
    }

    private ChatSession createChatSessionIfNotExists(Long projectId, Long userId) {
        ChatSessionId chatSessionId = new ChatSessionId(projectId, userId);
        ChatSession chatSession = chatSessionRepository.findById(chatSessionId).orElse(null);

        if(chatSession == null) {
            chatSession = ChatSession.builder()
                    .id(chatSessionId)
                    .build();

            chatSession = chatSessionRepository.save(chatSession);
        }
        return chatSession;
    }
}
