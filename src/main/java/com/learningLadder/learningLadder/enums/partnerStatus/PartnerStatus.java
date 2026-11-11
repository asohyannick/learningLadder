package com.learningLadder.learningLadder.enums.partnerStatus;

public enum PartnerStatus {
    PENDING,
    ACTIVE,
    INACTIVE,
    EXPIRED;
    public boolean isPublic() {
        return this == ACTIVE;
    }
}