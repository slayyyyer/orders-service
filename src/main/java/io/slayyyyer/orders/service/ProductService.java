package io.slayyyyer.orders.service;

import io.slayyyyer.orders.domain.Product;
import io.slayyyyer.orders.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    public List<Product> search(String query, int limit, int offset) {
        return products.search(query, limit, offset);
    }

    public long count(String query) {
        return products.count(query);
    }
}
