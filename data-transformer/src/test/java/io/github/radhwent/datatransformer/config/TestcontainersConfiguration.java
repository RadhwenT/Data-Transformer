package io.github.radhwent.datatransformer.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Fournit un vrai Postgres jetable (Docker) pour les tests, au lieu de la
 * base de dev partagee sur localhost:5432. Spring Boot demarre/arrete le
 * conteneur automatiquement (cycle de vie lie au contexte de test) et
 * reconfigure spring.datasource.* pour pointer dessus grace a
 * @ServiceConnection.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16"));
    }
}
