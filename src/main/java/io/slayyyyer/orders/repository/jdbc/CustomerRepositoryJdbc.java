package io.slayyyyer.orders.repository.jdbc;

import io.slayyyyer.orders.domain.Customer;
import io.slayyyyer.orders.repository.CustomerRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepositoryJdbc implements CustomerRepository {

    private final JdbcClient jdbc;

    public CustomerRepositoryJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return jdbc.sql("""
                select id, email, full_name, created_at
                  from customers
                 where id = :id
                """)
                .param("id", id)
                .query(CustomerRepositoryJdbc::mapCustomer)
                .optional();
    }

    @Override
    public List<Customer> findAll(int limit, int offset) {
        return jdbc.sql("""
                select id, email, full_name, created_at
                  from customers
                 order by created_at, id
                 limit :limit offset :offset
                """)
                .param("limit", limit)
                .param("offset", offset)
                .query(CustomerRepositoryJdbc::mapCustomer)
                .list();
    }

    private static Customer mapCustomer(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Customer(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("full_name"),
                rs.getTimestamp("created_at").toInstant());
    }
}
