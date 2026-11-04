package io.slayyyyer.orders.service;

import io.slayyyyer.orders.domain.Customer;
import io.slayyyyer.orders.repository.CustomerRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    public Customer require(UUID id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("customer", id));
    }

    public List<Customer> list(int limit, int offset) {
        return customers.findAll(limit, offset);
    }
}
