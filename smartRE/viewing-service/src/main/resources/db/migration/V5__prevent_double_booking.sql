-- Fix for a check-then-insert race in ViewingService.schedule(): two concurrent requests could
-- both pass the "no existing active viewing" application-level check before either had committed,
-- resulting in duplicate active bookings. Partial unique indexes make the database the actual
-- source of truth for this rule, closing the race regardless of application-level timing.
--
-- Two things are protected, matching the two ways a "double booking" was reported:
--   1. The same buyer ending up with more than one concurrently-active viewing request for the
--      same property.
--   2. Two different buyers both being booked into the exact same time slot for the same
--      property (the seller can't be in two viewings at once).
-- Only rows in an "active" status (PENDING_FEE/REQUESTED/CONFIRMED) participate — a cancelled,
-- completed, or no-show viewing never blocks a new booking for that buyer or that slot.
CREATE UNIQUE INDEX IF NOT EXISTS uq_viewing_active_buyer_property
    ON viewings (property_id, buyer_id)
    WHERE status IN ('PENDING_FEE', 'REQUESTED', 'CONFIRMED');

CREATE UNIQUE INDEX IF NOT EXISTS uq_viewing_active_property_slot
    ON viewings (property_id, scheduled_at)
    WHERE status IN ('PENDING_FEE', 'REQUESTED', 'CONFIRMED');
