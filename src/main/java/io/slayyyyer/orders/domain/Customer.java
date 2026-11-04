package io.slayyyyer.orders.domain;

import java.time.Instant;
import java.util.UUID;

public record Customer(UUID id, String email, String fullName, Instant createdAt) {

    public Customer {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("email must be a valid address");
        }
    }
}
