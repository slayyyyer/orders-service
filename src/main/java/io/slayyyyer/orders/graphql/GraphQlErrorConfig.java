package io.slayyyyer.orders.graphql;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import io.slayyyyer.orders.service.ConflictException;
import io.slayyyyer.orders.service.NotFoundException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;

/**
 * GraphQL and REST share the same domain exceptions but not the same error shape: GraphQL
 * gets typed errors with a classification, REST gets problem details. Keeping the mapping
 * explicit stops one transport's conventions leaking into the other.
 */
@Configuration
public class GraphQlErrorConfig {

    @Bean
    public DataFetcherExceptionResolver domainExceptionResolver() {
        return new DataFetcherExceptionResolverAdapter() {
            @Override
            protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
                if (ex instanceof NotFoundException notFound) {
                    return error(env, notFound.getMessage(), ErrorType.DataFetchingException)
                            .extensions(java.util.Map.of(
                                    "code", "NOT_FOUND",
                                    "resource", notFound.resource(),
                                    "id", notFound.id()))
                            .build();
                }
                if (ex instanceof ConflictException conflict) {
                    return error(env, conflict.getMessage(), ErrorType.ExecutionAborted)
                            .extensions(java.util.Map.of("code", "CONFLICT"))
                            .build();
                }
                if (ex instanceof IllegalArgumentException invalid) {
                    return error(env, invalid.getMessage(), ErrorType.ValidationError)
                            .extensions(java.util.Map.of("code", "INVALID_REQUEST"))
                            .build();
                }
                return null;
            }
        };
    }

    private static GraphqlErrorBuilder<?> error(
            DataFetchingEnvironment env, String message, ErrorType type) {
        return GraphqlErrorBuilder.newError(env)
                .message(message)
                .errorType(type);
    }
}
