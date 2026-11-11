package com.learningLadder.learningLadder.enums.aiMessageRole;

public enum AIMessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL;

    public String apiValue() {
        return name().toLowerCase();
    }
}