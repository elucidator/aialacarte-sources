package com.example.conferenceplanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class UiAssetsIT : PostgresIntegrationTest() {

    @Test
    fun `index page and static assets are served by the backend`() {
        val index = rest.getForEntity("/", String::class.java)
        assertThat(index.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(index.body).contains("Conference Planner")
        assertThat(index.body).contains("DEMO identity selector")
        assertThat(index.body).contains("Local demo panel")

        val script = rest.getForEntity("/app.js", String::class.java)
        assertThat(script.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(script.body).contains("Idempotency-Key")
        assertThat(script.body).contains("/api/demo/dispatch")

        val styles = rest.getForEntity("/styles.css", String::class.java)
        assertThat(styles.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(styles.body).contains("--accent")
    }
}
