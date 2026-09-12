package com.project.distributed_codegen.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "jwt.secretKey=test-only-jwt-key-with-at-least-32-bytes",
        "app.cors.allowed-origins=https://example.test"
})
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
