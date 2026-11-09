package com.apsonic.backend.shared.exception;

import com.apsonic.backend.shared.api.ErrorResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/** What an administrator sees when something goes wrong: a clear sentence and a reference, never SQL. */
class ErrorMessagesTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearTrace() {
        MDC.clear();
    }

    private static DataIntegrityViolationException violation(String sqlState, String constraint) {
        SQLException sql = new SQLException("ERROR: duplicate key value violates unique constraint \"" + constraint
                + "\"\n  Detail: Key (email)=(a@b.test) already exists.", sqlState);
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("could not execute statement", sql, constraint));
    }

    @Test
    void duplicateEmailIsAConflictWithAPlainSentence() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(
                violation("23505", "users_email_active_key"), new MockHttpServletRequest("POST", "/api/v1/admin/users"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("An account with this email address already exists.");
    }

    @Test
    void duplicateUsernameAndSlugHaveTheirOwnSentences() {
        assertThat(DatabaseConflictTranslator.uniqueMessage("users_username_active_key"))
                .isEqualTo("This username is already taken.");
        assertThat(DatabaseConflictTranslator.uniqueMessage("cms_pages_slug_key"))
                .contains("slug");
        assertThat(DatabaseConflictTranslator.uniqueMessage("something_else"))
                .isEqualTo("An item with the same unique value already exists.");
    }

    @Test
    void otherIntegrityProblemsMapToTheRightStatus() {
        assertThat(DatabaseConflictTranslator.translate(violation("23503", "fk_x")).status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(DatabaseConflictTranslator.translate(violation("23502", "nn")).status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(DatabaseConflictTranslator.translate(violation("22001", "len")).status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(DatabaseConflictTranslator.translate(new DataIntegrityViolationException("x")).message())
                .isEqualTo("This change conflicts with existing data.");
    }

    @Test
    void noSqlLeaksIntoTheAnswer() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(
                violation("23505", "users_email_active_key"), new MockHttpServletRequest("POST", "/x"));
        String body = response.getBody().getMessage();

        assertThat(body).doesNotContainIgnoringCase("constraint").doesNotContain("a@b.test").doesNotContain("users_");
    }

    @Test
    void unexpectedErrorsCarryAReferenceMatchingTheLog() {
        MDC.put("traceId", "6b47976d20a141e5");

        ResponseEntity<ErrorResponse> response = handler.handleUnhandledException(
                new IllegalStateException("boom: secret internals"), new MockHttpServletRequest("GET", "/x"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getTraceId()).isEqualTo("6b47976d20a141e5");
        assertThat(response.getBody().getMessage()).contains("6b47976d20a141e5").doesNotContain("secret internals");
    }

    @Test
    void aTraceIdThatIsNotAPlainTokenIsNeverEchoed() {
        MDC.put("traceId", "<script>alert(1)</script>");

        ResponseEntity<ErrorResponse> response = handler.handleUnhandledException(
                new IllegalStateException("boom"), new MockHttpServletRequest("GET", "/x"));

        assertThat(response.getBody().getTraceId()).isNull();
        assertThat(response.getBody().getMessage()).doesNotContain("script");
    }

    @Test
    void databaseOutageSaysTryAgainRatherThanReportingABug() {
        ResponseEntity<ErrorResponse> response = handler.handleDatabaseUnavailable(
                new org.springframework.dao.DataAccessResourceFailureException("connection refused"),
                new MockHttpServletRequest("GET", "/x"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getMessage()).contains("try again");
    }
}
