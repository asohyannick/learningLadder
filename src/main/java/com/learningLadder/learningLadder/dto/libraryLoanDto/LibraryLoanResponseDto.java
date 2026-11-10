package com.learningLadder.learningLadder.dto.libraryLoanDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.loanStatus.LoanStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LibraryLoanResponseDto(

        UUID id,
        String loanReference,

        UUID bookId,
        String bookTitle,
        String bookIsbn,
        UUID studentId,
        LoanStatus status,

        Instant reservedAt,
        Instant reservationExpiresAt,

        Instant borrowedAt,
        Instant dueAt,
        Instant returnRequestedAt,
        String returnRequestNote,
        Instant returnedAt,
        int renewalCount,
        Instant lastRenewedAt,

        UUID issuedBy,
        UUID receivedBy,
        String conditionOnBorrow,
        String conditionOnReturn,
        String notes,

        BigDecimal lateFee,
        String feeCurrency,
        boolean feePaid,
        Instant feePaidAt,

        boolean dueReminderSent,
        Instant dueReminderSentAt,

        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    public boolean overdue() {
        return (status == LoanStatus.BORROWED || status == LoanStatus.OVERDUE)
                && dueAt != null && Instant.now().isAfter(dueAt);
    }

    public long daysOverdue() {
        if (!overdue()) {
            return 0;
        }
        return Math.max(Duration.between(dueAt, Instant.now()).toDays(), 0);
    }
}