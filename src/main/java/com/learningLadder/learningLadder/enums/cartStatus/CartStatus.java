package com.learningLadder.learningLadder.enums.cartStatus;

public enum CartStatus {
    ACTIVE,
    CHECKED_OUT,
    ABANDONED;
    public boolean isEditable() {
        return this == ACTIVE || this == ABANDONED;
    }
}
