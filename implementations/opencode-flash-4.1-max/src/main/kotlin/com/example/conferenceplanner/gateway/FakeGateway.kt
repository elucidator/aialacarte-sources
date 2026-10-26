package com.example.conferenceplanner.gateway

import com.example.conferenceplanner.service.SeedData
import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * The fake notification gateway deliberately owns a second connection pool against the
 * same database. Every gateway write is therefore committed independently of the
 * caller's transaction: the sender can never roll back an accepted message.
 *
 * A production system would put this behind a network boundary; the separate pool is
 * the smallest local model with genuinely separate transaction ownership.
 */
@Configuration
class GatewayConfig {

    @Bean(name = ["gatewayDataSource"])
    fun gatewayDataSource(properties: DataSourceProperties): DataSource =
        properties.initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()
            .apply {
                poolName = "fake-gateway-pool"
                maximumPoolSize = 4
            }

    @Bean(name = ["gatewayJdbcTemplate"])
    fun gatewayJdbcTemplate(dataSource: DataSource): JdbcTemplate = JdbcTemplate(dataSource)

    @Bean
    fun fakeGateway(
        jdbc: JdbcTemplate,
        gatewayJdbc: JdbcTemplate,
    ): FakeGateway = FakeGateway(gatewayJdbc = gatewayJdbc, controlJdbc = jdbc)
}

enum class DeliveryOutcome { ACCEPTED, DUPLICATE }

class GatewayTimeoutException(message: String) : RuntimeException(message)

class FakeGateway(
    private val gatewayJdbc: JdbcTemplate,
    private val controlJdbc: JdbcTemplate,
) {
    /**
     * Durably records the confirmation keyed by [deliveryKey]. Repeats of the same key
     * are deduplicated and reported as DUPLICATE. When the demo failure control is
     * armed, the message has already been committed before this method throws a
     * simulated timeout, exactly like a gateway that accepted a message but lost its
     * acknowledgement.
     */
    fun deliver(deliveryKey: String, bookingId: Long): DeliveryOutcome {
        val inserted = gatewayJdbc.update(
            """
            insert into gateway_messages (delivery_key, booking_id, body)
            values (?, ?, ?)
            on conflict (delivery_key) do nothing
            """.trimIndent(),
            deliveryKey, bookingId, "confirmation for booking $bookingId",
        )
        val outcome = if (inserted == 1) DeliveryOutcome.ACCEPTED else DeliveryOutcome.DUPLICATE
        if (consumeSimulatedFailure()) {
            throw GatewayTimeoutException(
                "simulated timeout after the gateway durably recorded delivery '$deliveryKey'",
            )
        }
        return outcome
    }

    /** One-shot failure control: arming it makes the next dispatch attempt time out after acceptance. */
    private fun consumeSimulatedFailure(): Boolean =
        controlJdbc.update(
            "update demo_controls set value = 'false' where name = ? and value = 'true'",
            SeedData.FAILURE_SIMULATION_CONTROL,
        ) == 1
}
