-- Trip dates are derived from trip runtime configuration and day_number.
-- Keep the legacy column nullable for compatibility with existing installations,
-- but do not persist absolute calendar dates as itinerary facts.
ALTER TABLE trip_day MODIFY date DATE NULL;
UPDATE trip_day SET date = NULL;
