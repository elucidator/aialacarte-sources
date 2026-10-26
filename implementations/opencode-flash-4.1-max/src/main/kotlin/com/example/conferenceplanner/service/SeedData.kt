package com.example.conferenceplanner.service

/**
 * Deterministic seed data. Session ids are fixed and booking ids are assigned in a
 * stable order so that resets always restore the exact same state.
 */
object SeedData {

    data class SeedSession(val id: Long, val title: String, val room: String, val capacity: Int)

    data class SeedBooking(val id: Long, val sessionId: Long, val attendee: String)

    val sessions = listOf(
        SeedSession(1, "Debugging Distributed Systems", "Room A", 30),
        SeedSession(2, "Practical Kotlin", "Room B", 20),
        SeedSession(3, "Understanding Databases", "Room C", 40),
    )

    /** Fictional attendees used only for seed bookings. The demo identities Alice and Bob never appear here. */
    val fictionalAttendees = listOf(
        "Adrian Bellamy", "Bianca Torres", "Callum Osei", "Delia Marsh",
        "Emil Novak", "Farida Haddad", "Gustav Lindqvist", "Hana Sato",
        "Ivan Petrov", "Jasmine Cole", "Kwame Mensah", "Lucia Ferrari",
        "Mateo Rivas", "Nadia Rahman", "Oskar Berg", "Priya Nair",
        "Quentin Dubois", "Rosa Delgado", "Sanjay Iyer", "Tamar Levi",
        "Ursula Brandt", "Viktor Kowalski", "Wen Li", "Ximena Cruz",
        "Yusuf Demir", "Zara Ahmed", "Anton Hellström", "Beatrice Moreau",
        "Cesar Ortiz", "Dagmar Novotna", "Elias Weber", "Fiona Gallagher",
        "Georgios Papas", "Hilda Nkemdirim", "Isabel Santos", "Jonas Meyer",
        "Keiko Tanaka", "Lars Andersen", "Mirela Popescu", "Nils Johansson",
        "Omar Farouk",
    )

    /** 22 active bookings in session 1, 19 in session 2, 40 in session 3 = 81 bookings, ids 1..81. */
    val seedBookings: List<SeedBooking> = buildList {
        var id = 1L
        repeat(22) { i -> add(SeedBooking(id++, 1, fictionalAttendees[i])) }
        repeat(19) { i -> add(SeedBooking(id++, 2, fictionalAttendees[22 + i])) }
        repeat(40) { i -> add(SeedBooking(id++, 3, fictionalAttendees[i])) }
    }

    const val NEXT_BOOKING_ID = 82L
    const val FAILURE_SIMULATION_CONTROL = "simulate_notification_failure"
}
