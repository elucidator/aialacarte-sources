package conference

import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.web.bind.annotation.*
import javax.sql.DataSource

@Profile("local-demo")
@RestController
@RequestMapping("/api/demo")
class DemoController(private val jdbc: JdbcTemplate, manager: PlatformTransactionManager,
                     private val dataSource: DataSource, private val dispatcher: ConfirmationDispatcher) {
    private val transaction = TransactionTemplate(manager).apply { timeout = 15 }
    @PostMapping("/reset")
    fun reset(): Map<String, String> {
        transaction.executeWithoutResult {
            jdbc.execute("SELECT pg_advisory_xact_lock(731)")
            jdbc.execute("TRUNCATE confirmations, booking_requests, bookings, sessions, gateway_messages, gateway_attempts, activity RESTART IDENTITY CASCADE")
            jdbc.update("UPDATE demo_settings SET mode='normal' WHERE id=1")
            val connection = DataSourceUtils.getConnection(dataSource)
            try { ScriptUtils.executeSqlScript(connection, ClassPathResource("db/migration/V2__seed.sql")) }
            finally { DataSourceUtils.releaseConnection(connection, dataSource) }
        }
        return mapOf("status" to "reset")
    }
    @PostMapping("/mode")
    fun mode(@RequestBody input: ModeInput): Map<String, String> {
        if (input.mode !in setOf("normal", "fail", "lost-ack")) badInput("mode must be normal, fail or lost-ack.")
        transaction.executeWithoutResult {
            jdbc.execute("SELECT pg_advisory_xact_lock_shared(731)")
            jdbc.update("UPDATE demo_settings SET mode=? WHERE id=1", input.mode)
        }
        return mapOf("mode" to input.mode)
    }
    @PostMapping("/dispatch") fun dispatch() = dispatcher.dispatch()
    @GetMapping("/activity") fun activity(): Map<String, Any> = transaction.execute {
        jdbc.execute("SELECT pg_advisory_xact_lock_shared(731)")
        mapOf("mode" to jdbc.queryForObject("SELECT mode FROM demo_settings WHERE id=1", String::class.java)!!,
            "pending" to jdbc.queryForObject("SELECT count(*) FROM confirmations WHERE NOT acknowledged", Long::class.java)!!,
            "attempts" to jdbc.queryForList("SELECT * FROM gateway_attempts ORDER BY id DESC LIMIT 100"),
            "accepted" to jdbc.queryForList("SELECT * FROM gateway_messages ORDER BY accepted_at, delivery_key LIMIT 100"),
            "events" to jdbc.queryForList("SELECT * FROM activity ORDER BY id DESC LIMIT 100"))
    }!!
}
