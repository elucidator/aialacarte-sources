package dev.conferenceplanner.api

import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/** Machine-readable error codes. The HTTP status is fixed per code; see README "Error codes". */
enum class ErrorCode(val status: HttpStatus) {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MISSING_ATTENDEE(HttpStatus.BAD_REQUEST),
    UNKNOWN_ATTENDEE(HttpStatus.BAD_REQUEST),
    MISSING_REQUEST_KEY(HttpStatus.BAD_REQUEST),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    SESSION_FULL(HttpStatus.CONFLICT),
    ALREADY_BOOKED(HttpStatus.CONFLICT),
    REQUEST_KEY_REUSED(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
}

class ApiException(val code: ErrorCode, message: String) : RuntimeException(message)

data class ApiError(val code: String, val message: String, val status: Int)

@RestControllerAdvice
class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun api(e: ApiException) = respond(e.code, e.message ?: e.code.name)

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        ServletRequestBindingException::class,
    )
    fun malformed(e: Exception) = respond(ErrorCode.VALIDATION_ERROR, "Malformed request: ${e.message?.lineSequence()?.first()}")

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun method(e: HttpRequestMethodNotSupportedException) = respond(ErrorCode.VALIDATION_ERROR, e.message ?: "Method not supported")

    @ExceptionHandler(NoResourceFoundException::class)
    fun noResource(e: NoResourceFoundException) = respond(ErrorCode.NOT_FOUND, "No such resource: /${e.resourcePath}")

    /** Database backstop for invariant 2 (unique active booking per attendee and session). */
    @ExceptionHandler(DuplicateKeyException::class)
    fun duplicate(e: DuplicateKeyException) = respond(ErrorCode.ALREADY_BOOKED, "Attendee already has an active booking for this session")

    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception): ResponseEntity<ApiError> {
        log.error("Unhandled error", e)
        return respond(ErrorCode.INTERNAL_ERROR, "Unexpected error")
    }

    private fun respond(code: ErrorCode, message: String): ResponseEntity<ApiError> =
        ResponseEntity.status(code.status).body(ApiError(code.name, message, code.status.value()))
}
