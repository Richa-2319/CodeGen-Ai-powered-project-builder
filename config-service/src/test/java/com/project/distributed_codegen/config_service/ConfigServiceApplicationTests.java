package com.project.distributed_codegen.config_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.server.git.uri=https://example.invalid/config.git",
        "spring.cloud.config.server.git.clone-on-start=false"
})
class ConfigServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
