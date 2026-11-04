package io.slayyyyer.orders.repository;

import io.slayyyyer.orders.domain.Product;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ProductRepository {

    List<Product> search(String query, int limit, int offset);

    long count(String query);

    Map<UUID, Product> findAllByIds(Collection<UUID> ids);
}
