package com.project.distributed_codegen.account_service;

import com.project.distributed_codegen.account_service.entity.Plan;
import com.project.distributed_codegen.account_service.entity.User;
import com.project.distributed_codegen.account_service.repository.PlanRepository;
import com.project.distributed_codegen.account_service.repository.SubscriptionRepository;
import com.project.distributed_codegen.account_service.repository.UserRepository;
import com.project.distributed_codegen.account_service.service.SubscriptionService;
import com.project.distributed_codegen.common_lib.enums.SubscriptionStatus;
import com.project.distributed_codegen.common_lib.security.JwtUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:account;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"jwt.secret-key=test-only-jwt-key-with-at-least-32-bytes",
		"internal.service-key=test-only-internal-key",
		"stripe.api.secret=test-only",
		"stripe.webhook.secret=test-only"
})
class AccountServiceApplicationTests {

    @Autowired private PlanRepository plans;
    @Autowired private UserRepository users;
    @Autowired private SubscriptionRepository subscriptions;
    @Autowired private SubscriptionService subscriptionService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void paidPlanCanBeReadAcrossRepositoryBoundariesWithOpenInViewDisabled() {
        User user = users.save(User.builder().username("plan@example.test").name("Test")
                .password("test-only-placeholder").build());
        Plan plan = new Plan();
        plan.setName("Test paid plan");
        plan.setMaxProjects(10);
        plan.setMaxTokensPerDay(20000);
        plan.setMaxPreviews(1);
        plan.setUnlimitedAi(false);
        plan.setActive(true);
        plan = plans.save(plan);
        subscriptionService.activateSubscription(user.getId(), plan.getId(), "test-subscription", "test-customer");
        assertThat(subscriptions.findByStripeSubscriptionId("test-subscription").orElseThrow().getCancelAtPeriodEnd())
                .isFalse();
        subscriptionService.updateSubscription("test-subscription", SubscriptionStatus.ACTIVE, null, null, null, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new JwtUserPrincipal(user.getId(), "Test", user.getUsername(), null, List.of()),
                "test-only-token", List.of()));

        assertThat(subscriptionService.getCurrentSubscribedPlanByUser().maxProjects()).isEqualTo(10);
        assertThat(subscriptionService.getCurrentSubscription().plan().name()).isEqualTo("Test paid plan");
    }

	@Test
	void contextLoads() {
	}

}
