package com.conference.planner

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.profiles.active=local-demo"],
)
abstract class PostgresIntegrationTest {
    companion object {
        /**
         * Single shared PostgreSQL for all IT classes in this JVM.
         * Started once; avoids per-class container lifecycle races.
         */
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("conference")
                .withUsername("conference")
                .withPassword("conference")
                .apply { start() }

        @DynamicPropertySource
        @JvmStatic
        fun datasourceProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
