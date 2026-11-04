package io.slayyyyer.orders.web.rest;

import io.slayyyyer.orders.service.ProductService;
import io.slayyyyer.orders.web.dto.PagedResponse;
import io.slayyyyer.orders.web.dto.ProductResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    public PagedResponse<ProductResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }

        List<ProductResponse> items = products.search(query, size, page * size).stream()
                .map(ProductResponse::from)
                .toList();
        long total = products.count(query);

        return PagedResponse.of(items, page, size, total, (long) (page + 1) * size < total);
    }
}
