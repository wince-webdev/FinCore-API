package com.wince.fincore;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// PostgreSQL 17 imposé par le cahier des charges (§3) — ne jamais
		// utiliser `latest` : une image qui bouge casse la reproductibilité
		// des tests.
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

}
