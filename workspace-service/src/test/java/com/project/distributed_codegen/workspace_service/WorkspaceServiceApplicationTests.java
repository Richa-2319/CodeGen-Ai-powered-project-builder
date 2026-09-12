package com.project.distributed_codegen.workspace_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:workspace;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"spring.kafka.listener.auto-startup=false",
		"jwt.secret-key=test-only-jwt-key-with-at-least-32-bytes",
		"internal.service-key=test-only-internal-key",
		"minio.url=http://localhost:9000",
		"minio.access-key=test-only",
		"minio.secret-key=test-only",
		"minio.project-bucket=projects",
		"app.preview.enabled=false",
		"app.preview.namespace=codegen-previews",
		"app.preview.domain=example.test",
		"app.preview.proxy-port=443"
})
class WorkspaceServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
