package io.slayyyyer.orders.web.dto;

import io.slayyyyer.orders.domain.Customer;
import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(UUID id, String email, String fullName, Instant createdAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.id(), customer.email(), customer.fullName(), customer.createdAt());
    }
}
