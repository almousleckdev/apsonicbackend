package com.apsonic.backend.shared.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a database integrity error into a message an administrator can act on. The SQL itself (table, column and
 * constraint names, the offending value) is never part of the answer.
 */
final class DatabaseConflictTranslator {

    record Translation(HttpStatus status, String message) {
    }

    private static final Pattern CONSTRAINT_IN_MESSAGE = Pattern.compile("constraint \"([^\"]+)\"");

    private DatabaseConflictTranslator() {
    }

    static Translation translate(DataIntegrityViolationException ex) {
        return switch (sqlState(ex)) {
            case "23505" -> new Translation(HttpStatus.CONFLICT, uniqueMessage(constraintName(ex)));
            case "23503" -> new Translation(HttpStatus.CONFLICT,
                    "This item is still used by other content, or refers to something that no longer exists.");
            case "23502" -> new Translation(HttpStatus.BAD_REQUEST, "A required value is missing.");
            case "22001" -> new Translation(HttpStatus.BAD_REQUEST, "A value is too long for its field.");
            default -> new Translation(HttpStatus.CONFLICT, "This change conflicts with existing data.");
        };
    }

    static String uniqueMessage(String constraint) {
        String name = constraint == null ? "" : constraint.toLowerCase();
        if (name.contains("users_email")) {
            return "An account with this email address already exists.";
        }
        if (name.contains("users_username")) {
            return "This username is already taken.";
        }
        if (name.contains("slug")) {
            return "This address (slug) is already used by another item.";
        }
        return "An item with the same unique value already exists.";
    }

    private static String sqlState(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException hibernate && hibernate.getSQLState() != null) {
                return hibernate.getSQLState();
            }
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return "";
    }

    private static String constraintName(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException hibernate && hibernate.getConstraintName() != null) {
                return hibernate.getConstraintName();
            }
            if (t.getMessage() != null) {
                Matcher m = CONSTRAINT_IN_MESSAGE.matcher(t.getMessage());
                if (m.find()) {
                    return m.group(1);
                }
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
    }
}
