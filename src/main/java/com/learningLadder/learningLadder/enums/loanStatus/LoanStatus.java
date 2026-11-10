package com.learningLadder.learningLadder.enums.loanStatus;

public enum LoanStatus {

    RESERVED,
    BORROWED,
    OVERDUE,
    RETURNED,
    LOST,
    CANCELLED,
    RETURN_REQUESTED;
    public boolean isActive() {
        return this == RESERVED || this == BORROWED || this == OVERDUE || this == RETURN_REQUESTED;
    }
    public boolean isFinal() {
        return this == RETURNED || this == LOST || this == CANCELLED;
    }
}