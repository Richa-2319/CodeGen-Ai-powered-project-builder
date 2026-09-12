package com.project.distributed_codegen.intelligence_service.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.Usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AiTokenUsageTest {
    @Test
    void estimatesWhenProviderReturnsAnEmptyUsageObject() {
        var counts = AiGenerationServiceImpl.tokenCounts(mock(Usage.class), "Create an app", "<div>Hello</div>");
        assertThat(counts.prompt()).isPositive();
        assertThat(counts.completion()).isPositive();
        assertThat(counts.total()).isEqualTo(counts.prompt() + counts.completion());
    }

    @Test
    void preservesReportedTotalsIncludingUnseenReasoningTokens() {
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(20);
        when(usage.getTotalTokens()).thenReturn(80);
        assertThat(AiGenerationServiceImpl.tokenCounts(usage, "prompt", "result"))
                .isEqualTo(new AiGenerationServiceImpl.TokenCounts(10, 20, 80));
    }

    @Test
    void estimatesMissingCountsWithoutReplacingReportedCounts() {
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(null);
        when(usage.getTotalTokens()).thenReturn(null);
        var counts = AiGenerationServiceImpl.tokenCounts(usage, "prompt", "result");
        assertThat(counts.prompt()).isEqualTo(10);
        assertThat(counts.completion()).isPositive();
        assertThat(counts.total()).isEqualTo(10 + counts.completion());
    }

    @Test
    void doesNotInventUsageForEmptyText() {
        assertThat(AiGenerationServiceImpl.tokenCounts(null, "", null))
                .isEqualTo(new AiGenerationServiceImpl.TokenCounts(0, 0, 0));
    }
}
