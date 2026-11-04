package io.slayyyyer.orders.config;

import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * GraphQL knows Int, Float, String, Boolean and ID. It does not know {@code Long}, but the
 * schema needs one: money is stored in cents, and cents do not fit in a 32-bit Int for
 * anything that is not a hobby project.
 *
 * <p>Without this, building the {@code GraphQlSource} fails with
 * {@code There is no scalar implementation for the named 'Long' scalar type} — at startup,
 * so no unit test notices it.
 */
@Configuration
public class GraphQlScalarConfig {

    private static final GraphQLScalarType LONG = GraphQLScalarType.newScalar()
            .name("Long")
            .description("A signed 64-bit integer. Used for monetary amounts in cents.")
            .coercing(new LongCoercing())
            .build();

    @Bean
    public RuntimeWiringConfigurer longScalarWiring() {
        return wiring -> wiring.scalar(LONG);
    }

    /**
     * The single-argument coercing methods are the ones every graphql-java version still
     * routes through, so implementing them keeps this independent of the exact release.
     */
    static final class LongCoercing implements Coercing<Long, Long> {

        @Override
        public Long serialize(Object dataFetcherResult) {
            return toLong(dataFetcherResult, CoercingSerializeException::new);
        }

        @Override
        public Long parseValue(Object input) {
            return toLong(input, CoercingParseValueException::new);
        }

        @Override
        public Long parseLiteral(Object input) {
            if (input instanceof IntValue intValue) {
                return intValue.getValue().longValue();
            }
            if (input instanceof StringValue stringValue) {
                try {
                    return Long.parseLong(stringValue.getValue());
                } catch (NumberFormatException e) {
                    throw new CoercingParseLiteralException("Cannot parse \"" + stringValue.getValue() + "\" as a Long");
                }
            }
            throw new CoercingParseLiteralException("Expected an integer literal for Long");
        }

        private static Long toLong(Object value, java.util.function.Function<String, RuntimeException> error) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String text) {
                try {
                    return Long.parseLong(text);
                } catch (NumberFormatException e) {
                    throw error.apply("Cannot parse \"" + text + "\" as a Long");
                }
            }
            throw error.apply("Cannot represent " + value + " as a Long");
        }
    }
}
