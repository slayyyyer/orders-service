package io.slayyyyer.orders;

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
 * an integration test only reports "expected 201 but was 500", because the CI job's logs are
 * not readable without credentials.
 *
 * <p>This advice sits ahead of the application's own handlers and returns the exception
 * chain as one short line: the CI step that surfaces failures keeps only six lines per
 * report, so a full stack trace pushes the root cause out of view.
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
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("diagnostic", describe(failure));
            return ResponseEntity.internalServerError().body(body);
        }

        private static String describe(Throwable failure) {
            StringBuilder text = new StringBuilder();
            Throwable current = failure;
            for (int depth = 0; current != null && depth < 4; depth++) {
                if (depth > 0) {
                    text.append(" <- ");
                }
                text.append(current.getClass().getSimpleName());
                String message = current.getMessage();
                if (message != null) {
                    text.append(": ").append(message.replace('\n', ' ').strip());
                }
                current = current.getCause();
            }
            return text.toString();
        }
    }
}
