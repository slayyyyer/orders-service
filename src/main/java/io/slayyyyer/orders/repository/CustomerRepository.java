package io.slayyyyer.orders.repository;

import io.slayyyyer.orders.domain.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

    Optional<Customer> findById(UUID id);

    List<Customer> findAll(int limit, int offset);
}
