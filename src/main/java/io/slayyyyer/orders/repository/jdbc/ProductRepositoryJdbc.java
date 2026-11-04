package io.slayyyyer.orders.repository.jdbc;

import io.slayyyyer.orders.domain.Product;
import io.slayyyyer.orders.repository.ProductRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepositoryJdbc implements ProductRepository {

    private final JdbcClient jdbc;

    public ProductRepositoryJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Product> search(String query, int limit, int offset) {
        return jdbc.sql("""
                select id, sku, name, unit_price_cents
                  from products
                 where (:query is null or name ilike :pattern or sku ilike :pattern)
                 order by name, id
                 limit :limit offset :offset
                """)
                .param("query", blankToNull(query))
                .param("pattern", "%" + (query == null ? "" : query.trim()) + "%")
                .param("limit", limit)
                .param("offset", offset)
                .query(ProductRepositoryJdbc::mapProduct)
                .list();
    }

    @Override
    public long count(String query) {
        Long total = jdbc.sql("""
                select count(*)
                  from products
                 where (:query is null or name ilike :pattern or sku ilike :pattern)
                """)
                .param("query", blankToNull(query))
                .param("pattern", "%" + (query == null ? "" : query.trim()) + "%")
                .query(Long.class)
                .single();
        return total == null ? 0L : total;
    }

    @Override
    public Map<UUID, Product> findAllByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Product> found = jdbc.sql("""
                select id, sku, name, unit_price_cents
                  from products
                 where id in (:ids)
                """)
                .param("ids", ids)
                .query(ProductRepositoryJdbc::mapProduct)
                .list();

        Map<UUID, Product> byId = new LinkedHashMap<>();
        for (Product product : found) {
            byId.put(product.id(), product);
        }
        return byId;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Product mapProduct(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Product(
                rs.getObject("id", UUID.class),
                rs.getString("sku"),
                rs.getString("name"),
                rs.getLong("unit_price_cents"));
    }
}
