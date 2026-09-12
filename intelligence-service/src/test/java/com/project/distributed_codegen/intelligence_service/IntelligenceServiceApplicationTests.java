package com.project.distributed_codegen.intelligence_service;

import com.project.distributed_codegen.common_lib.enums.ChatEventStatus;
import com.project.distributed_codegen.common_lib.enums.ChatEventType;
import com.project.distributed_codegen.common_lib.enums.MessageRole;
import com.project.distributed_codegen.common_lib.enums.ProjectPermission;
import com.project.distributed_codegen.common_lib.security.JwtUserPrincipal;
import com.project.distributed_codegen.intelligence_service.client.AccountClient;
import com.project.distributed_codegen.intelligence_service.client.WorkspaceClient;
import com.project.distributed_codegen.intelligence_service.entity.ChatEvent;
import com.project.distributed_codegen.intelligence_service.entity.ChatMessage;
import com.project.distributed_codegen.intelligence_service.entity.ChatSession;
import com.project.distributed_codegen.intelligence_service.entity.ChatSessionId;
import com.project.distributed_codegen.intelligence_service.repository.ChatEventRepository;
import com.project.distributed_codegen.intelligence_service.repository.ChatMessageRepository;
import com.project.distributed_codegen.intelligence_service.repository.ChatSessionRepository;
import com.project.distributed_codegen.intelligence_service.service.ChatService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:intelligence;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"spring.kafka.listener.auto-startup=false",
		"spring.ai.openai.api-key=test-only",
		"jwt.secret-key=test-only-jwt-key-with-at-least-32-bytes",
		"internal.service-key=test-only-internal-key"
})
class IntelligenceServiceApplicationTests {

    private static final List<String> downstreamRequests = new CopyOnWriteArrayList<>();
    private static final HttpServer downstream = startDownstream();

    @Autowired private AccountClient account;
    @Autowired private WorkspaceClient workspace;
    @Autowired private ChatSessionRepository sessions;
    @Autowired private ChatMessageRepository messages;
    @Autowired private ChatEventRepository events;
    @Autowired private ChatService chats;
    @Autowired private ChatClient chatClient;

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry properties) {
        String baseUrl = "http://127.0.0.1:" + downstream.getAddress().getPort();
        properties.add("services.account.url", () -> baseUrl);
        properties.add("services.workspace.url", () -> baseUrl);
        properties.add("AI_BASE_URL", () -> baseUrl);
        properties.add("AI_CHAT_COMPLETIONS_PATH", () -> "/20231130/actions/v1/chat/completions");
        properties.add("AI_MODEL", () -> "test-only-model");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    static void stopDownstream() {
        downstream.stop(0);
    }

    @Test
    void realFeignRequestsUseServiceContextPathsAndBothAuthenticationHeaders() {
        authenticate();
        assertThat(account.getCurrentSubscribedPlanByUser().maxProjects()).isEqualTo(3);
        assertThat(workspace.checkPermission(7L, ProjectPermission.EDIT)).isTrue();
        SecurityContextHolder.clearContext();
        assertThat(workspace.getFileTree(7L, "Bearer test-only-request-token").files()).isEmpty();
        assertThat(workspace.getFileContent(7L, "src/App.tsx", "Bearer test-only-request-token"))
                .isEqualTo("source");

        assertThat(downstreamRequests).contains(
                "/account/internal/v1/billing/current-plan",
                "/workspace/internal/v1/projects/7/permissions/check?permission=EDIT",
                "/workspace/internal/v1/projects/7/files/tree",
                "/workspace/internal/v1/projects/7/files/content?path=src/App.tsx");
    }

    @Test
    void chatHistoryMapsPersistedEventsWithOpenInViewDisabled() {
        ChatSession session = sessions.save(ChatSession.builder().id(new ChatSessionId(17L, 2L)).build());
        ChatMessage message = messages.save(ChatMessage.builder().chatSession(session)
                .role(MessageRole.ASSISTANT).content("test response").tokensUsed(1).build());
        events.save(ChatEvent.builder().chatMessage(message).type(ChatEventType.MESSAGE)
                .status(ChatEventStatus.CONFIRMED).sequenceOrder(1).content("test event").build());
        authenticate();

        assertThat(chats.getProjectChatHistory(17L)).singleElement().satisfies(response ->
                assertThat(response.events()).singleElement().satisfies(event ->
                        assertThat(event.content()).isEqualTo("test event")));
    }

    @Test
    void configuredProviderReceivesAuthenticatedStreamingRequestAtExactPath() {
        List<String> chunks = chatClient.prompt().user("Test connection")
                .stream().content().collectList().block(Duration.ofSeconds(15));

        assertThat(chunks).isNotNull();
        assertThat(String.join("", chunks)).isEqualTo("configured provider");
        assertThat(downstreamRequests).contains("/20231130/actions/v1/chat/completions");
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new JwtUserPrincipal(2L, "Test", "test@example.test", null, List.of()),
                "test-only-request-token", List.of()));
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                downstreamRequests.add(exchange.getRequestURI().getPath() + (exchange.getRequestURI().getQuery() == null ? "" : "?" + exchange.getRequestURI().getQuery()));
                if (exchange.getRequestURI().getPath().equals("/20231130/actions/v1/chat/completions")) {
                    var request = JsonMapper.builder().build().readTree(exchange.getRequestBody().readAllBytes());
                    boolean valid = "POST".equals(exchange.getRequestMethod())
                            && "Bearer test-only".equals(exchange.getRequestHeaders().getFirst("Authorization"))
                            && "test-only-model".equals(request.path("model").asText())
                            && request.path("stream").asBoolean()
                            && request.path("max_completion_tokens").asInt() == 2048;
                    String body = "data: {\"id\":\"test-completion\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test-only-model\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"configured provider\"},\"finish_reason\":null}]}\n\n"
                            + "data: [DONE]\n\n";
                    byte[] bytes = (valid ? body : "{}").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", valid ? "text/event-stream" : "application/json");
                    exchange.sendResponseHeaders(valid ? 200 : 400, bytes.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(bytes);
                    }
                    return;
                }
                boolean authenticated = "Bearer test-only-request-token".equals(
                        exchange.getRequestHeaders().getFirst("Authorization"))
                        && "test-only-internal-key".equals(
                        exchange.getRequestHeaders().getFirst("X-Internal-Service-Key"));
                String path = exchange.getRequestURI().getPath();
                String body = switch (path) {
                    case "/account/internal/v1/billing/current-plan" ->
                            "{\"id\":0,\"name\":\"Free\",\"maxProjects\":3,\"maxTokensPerDay\":5000,\"unlimitedAi\":false,\"price\":\"0\"}";
                    case "/workspace/internal/v1/projects/7/permissions/check" -> "true";
                    case "/workspace/internal/v1/projects/7/files/tree" -> "{\"files\":[]}";
                    case "/workspace/internal/v1/projects/7/files/content" -> "source";
                    default -> "{}";
                };
                exchange.getResponseHeaders().set("Content-Type", path.endsWith("/content") ? "text/plain" : "application/json");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(authenticated ? 200 : 401, bytes.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start local HTTP test server", exception);
        }
    }

	@Test
	void contextLoads() {
	}

}
