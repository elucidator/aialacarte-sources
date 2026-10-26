package conference

import org.springframework.http.HttpStatus

data class Session(val id: Long, val title: String, val room: String, val capacity: Int,
                   val activeBookings: Int, val availablePlaces: Int)
data class SessionPage(val items: List<Session>, val page: Int, val size: Int, val totalElements: Long)
data class Booking(val id: String, val sessionId: Long, val title: String, val attendee: String, val status: String)
data class BookingInput(val sessionId: Long)
data class BookingResult(val booking: Booking, val replay: Boolean)
data class ModeInput(val mode: String)
data class ApiError(val code: String, val message: String)
class ApiProblem(val status: HttpStatus, val code: String, override val message: String) : RuntimeException(message)
fun badInput(message: String): Nothing = throw ApiProblem(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message)
fun missing(message: String): Nothing = throw ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", message)
fun conflict(code: String, message: String): Nothing = throw ApiProblem(HttpStatus.CONFLICT, code, message)
