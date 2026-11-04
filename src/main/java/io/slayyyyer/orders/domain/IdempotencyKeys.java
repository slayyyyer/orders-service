package io.slayyyyer.orders.domain;

import java.util.regex.Pattern;

/**
 * A client-supplied idempotency key must be opaque, bounded and printable. Bounding it
 * keeps a hostile client from writing megabytes into a unique index.
 */
public final class IdempotencyKeys {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;
    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9._:-]+$");

    private IdempotencyKeys() {
    }

    public static String requireValid(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        String trimmed = key.trim();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        if (!ALLOWED.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Idempotency-Key may only contain letters, digits and . _ : -");
        }
        return trimmed;
    }
}
