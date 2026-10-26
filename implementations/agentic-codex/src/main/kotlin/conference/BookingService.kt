package conference

import org.springframework.stereotype.Service

@Service
class BookingService(private val repository: PostgresBookingRepository) {
    fun sessions(page: Int, size: Int): SessionPage {
        if (page < 0 || size !in 1..100) badInput("page must be non-negative and size must be 1..100.")
        return repository.sessions(page, size)
    }
    fun identity(value: String?): String {
        if (value == null || !value.matches(Regex("[a-z][a-z0-9-]{0,63}")))
            badInput("X-Demo-Attendee must be a demo identifier such as alice or bob.")
        return value
    }
    fun book(identity: String?, key: String?, input: BookingInput): BookingResult {
        val attendee = identity(identity)
        if (key == null || !key.matches(Regex("[A-Za-z0-9:_-]{1,128}")))
            badInput("Idempotency-Key must contain 1..128 letters, digits, colons, underscores or hyphens.")
        if (input.sessionId <= 0) badInput("sessionId must be positive.")
        return repository.book(attendee, key, input.sessionId)
    }
    fun bookings(identity: String?) = repository.bookings(identity(identity))
    fun cancel(identity: String?, id: String) = repository.cancel(identity(identity), id)
}
