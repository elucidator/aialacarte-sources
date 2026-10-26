package com.example.conferenceplanner

import org.testcontainers.containers.PostgreSQLContainer

/**
 * One PostgreSQL container per test JVM, matching the postgres:16-alpine image used by
 * Docker Compose. Spring test contexts are cached across test classes, so the container
 * must stay alive for the whole run.
 */
object TestDatabase {
    val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("conference")
        .withUsername("conference")
        .withPassword("conference")
        .apply { start() }
}
