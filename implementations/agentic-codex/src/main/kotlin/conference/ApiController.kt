package conference

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingServletRequestParameterException

@RestController
class ApiController(private val service: BookingService, private val jdbc: JdbcTemplate) {
    @GetMapping("/api/sessions")
    fun sessions(@RequestParam(required=false) page: String?, @RequestParam(required=false) size: String?) =
        service.sessions(integer(page, 0, "page"), integer(size, 20, "size"))
    private fun integer(value: String?, default: Int, name: String): Int =
        if (value == null) default else value.toIntOrNull() ?: badInput("$name must be an integer.")
    @PostMapping("/api/bookings")
    fun book(@RequestHeader("X-Demo-Attendee", required=false) identity: String?,
             @RequestHeader("Idempotency-Key", required=false) key: String?, @RequestBody input: BookingInput): ResponseEntity<Booking> {
        val result = service.book(identity, key, input)
        return ResponseEntity.status(if (result.replay) 200 else 201).body(result.booking)
    }
    @GetMapping("/api/bookings")
    fun bookings(@RequestHeader("X-Demo-Attendee", required=false) identity: String?) = service.bookings(identity)
    @DeleteMapping("/api/bookings/{id}")
    fun cancel(@RequestHeader("X-Demo-Attendee", required=false) identity: String?, @PathVariable id: String): ResponseEntity<Void> {
        service.cancel(identity, id)
        return ResponseEntity.noContent().build()
    }
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> = try {
        jdbc.queryForObject("SELECT count(*) FROM sessions", Long::class.java)
        ResponseEntity.ok(mapOf("status" to "ready"))
    } catch (_: Exception) { ResponseEntity.status(503).body(mapOf("status" to "unavailable")) }
}

@RestControllerAdvice
class Errors {
    @ExceptionHandler(ApiProblem::class)
    fun problem(e: ApiProblem) = ResponseEntity.status(e.status).body(ApiError(e.code, e.message))
    @ExceptionHandler(MethodArgumentTypeMismatchException::class, HttpMessageNotReadableException::class,
        MissingServletRequestParameterException::class)
    fun malformed(e: Exception) = ResponseEntity.badRequest().body(ApiError("INVALID_INPUT", "Malformed JSON or query parameter."))
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException::class)
    fun constraint(e: Exception) = ResponseEntity.status(409).body(ApiError("CONFLICT", "The request conflicts with an existing booking."))
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException::class)
    fun absent(e: Exception) = ResponseEntity.status(404).body(ApiError("NOT_FOUND", "Resource not found."))
    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception): ResponseEntity<ApiError> {
        org.slf4j.LoggerFactory.getLogger(Errors::class.java).error("Request failed", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError("INTERNAL_ERROR", "The request could not be completed."))
    }
}
