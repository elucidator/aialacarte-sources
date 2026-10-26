package com.example.conferenceplanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/** No local-demo profile: demo control routes must not exist. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoProfileDisabledIT {

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", TestDatabase.postgres::getJdbcUrl)
            registry.add("spring.datasource.username", TestDatabase.postgres::getUsername)
            registry.add("spring.datasource.password", TestDatabase.postgres::getPassword)
        }
    }

    @Autowired
    lateinit var rest: TestRestTemplate

    @Test
    fun `demo control endpoints are absent without the local-demo profile`() {
        val getState = rest.getForEntity("/api/demo/state", String::class.java)
        assertThat(getState.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(getState.body).contains("NOT_FOUND")

        val headers = HttpHeaders()
        val reset = rest.exchange("/api/demo/reset", HttpMethod.POST, HttpEntity<Void>(headers), String::class.java)
        assertThat(reset.statusCode).isEqualTo(HttpStatus.NOT_FOUND)

        val dispatch = rest.exchange("/api/demo/dispatch", HttpMethod.POST, HttpEntity<Void>(headers), String::class.java)
        assertThat(dispatch.statusCode).isEqualTo(HttpStatus.NOT_FOUND)

        val sessions = rest.getForEntity("/api/sessions", String::class.java)
        assertThat(sessions.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(sessions.body).contains("Debugging Distributed Systems")
    }
}
