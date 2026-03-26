package com.ragnarok.domain.exception;

public class InsufficientJobLevelException extends GameException {
    public InsufficientJobLevelException(int required) {
        super("Job level insuficiente. Necessário: " + required);
    }
}
