#!/usr/bin/env bash
# Structural acceptance for exercise 3, run inside an exported workspace.
set -u
fail=0
[[ -f src/main/kotlin/dev/conferenceplanner/booking/BookingRepository.kt ]] || { echo "missing BookingRepository.kt"; fail=1; }
grep -q "^interface BookingRepository" src/main/kotlin/dev/conferenceplanner/booking/BookingRepository.kt 2>/dev/null || { echo "BookingRepository is not an interface"; fail=1; }
grep -q ": BookingRepository" src/main/kotlin/dev/conferenceplanner/booking/PostgresBookingRepository.kt || { echo "PostgresBookingRepository does not implement BookingRepository"; fail=1; }
grep -q "PostgresBookingRepository" src/main/kotlin/dev/conferenceplanner/booking/BookingService.kt && { echo "BookingService still references PostgresBookingRepository"; fail=1; }
exit $fail
