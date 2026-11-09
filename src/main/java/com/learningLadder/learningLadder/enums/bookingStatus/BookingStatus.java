package com.learningLadder.learningLadder.enums.bookingStatus;

public enum BookingStatus {

    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED,
    COMPLETED,
    NO_SHOW;

    public boolean isActive() {
        return this == PENDING || this == CONFIRMED;
    }

    public boolean isFinal() {
        return this == REJECTED || this == CANCELLED || this == COMPLETED || this == NO_SHOW;
    }
}