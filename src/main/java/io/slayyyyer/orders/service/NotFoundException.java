package io.slayyyyer.orders.service;

/**
 * A missing resource. Deliberately transport-agnostic: the REST advice turns it into a
 * 404 problem detail and the GraphQL advice turns it into a typed error.
 */
public class NotFoundException extends RuntimeException {

    private final String resource;
    private final String id;

    public NotFoundException(String resource, Object id) {
        super(resource + " " + id + " was not found");
        this.resource = resource;
        this.id = String.valueOf(id);
    }

    public String resource() {
        return resource;
    }

    public String id() {
        return id;
    }
}
