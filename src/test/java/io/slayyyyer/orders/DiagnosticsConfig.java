package io.slayyyyer.orders;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Test-only window into server-side failures.
 *
 * <p>The application answers unexpected exceptions with a deliberately empty 500 (messages
 * are switched off so internals never leak). That is right for production and useless when
 * an integration test only reports "expected 201 but was 500", because the CI job's logs
 * are not readable without credentials. This advice sits ahead of the application's own
 * handlers and returns the exception and its stack, so the assertion output carries the
 * diagnosis. It exists only on the test classpath.
 */
@TestConfiguration
public class DiagnosticsConfig {

    @Bean
    public RevealExceptions revealExceptions() {
        return new RevealExceptions();
    }

    @RestControllerAdvice
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public static class RevealExceptions {

        @ExceptionHandler(Throwable.class)
        public ResponseEntity<Map<String, Object>> reveal(Throwable failure) {
            StringWriter stack = new StringWriter();
            failure.printStackTrace(new PrintWriter(stack));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("diagnostic", failure.getClass().getName() + ": " + failure.getMessage());
            body.put("stack", stack.toString());
            return ResponseEntity.internalServerError().body(body);
        }
    }
}
