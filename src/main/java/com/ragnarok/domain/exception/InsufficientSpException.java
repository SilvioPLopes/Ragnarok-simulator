package com.ragnarok.domain.exception;

public class InsufficientSpException extends GameException {
    public InsufficientSpException(int required, int current) {
        super("SP insuficiente. Necessário: " + required + ", atual: " + current);
    }
}
