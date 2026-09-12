package com.project.distributed_codegen.intelligence_service.controller;

import com.project.distributed_codegen.common_lib.security.AuthUtil;
import com.project.distributed_codegen.intelligence_service.entity.UsageLog;
import com.project.distributed_codegen.intelligence_service.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/usage")
@RequiredArgsConstructor
public class UsageController {
    private final AuthUtil auth;
    private final UsageLogRepository usage;

    @GetMapping("/today")
    public TodayUsage today() {
        LocalDate day = LocalDate.now();
        int tokens = usage.findByUserIdAndDate(auth.getCurrentUserId(), day)
                .map(UsageLog::getTokensUsed).orElse(0);
        return new TodayUsage(day, ZoneId.systemDefault().getId(), tokens);
    }

    public record TodayUsage(LocalDate date, String timeZone, int tokensUsed) {}
}
