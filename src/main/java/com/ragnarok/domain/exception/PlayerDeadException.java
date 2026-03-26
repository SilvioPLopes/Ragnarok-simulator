package com.ragnarok.domain.exception;

public class PlayerDeadException extends GameException {
    public PlayerDeadException() {
        super("O jogador está morto e não pode realizar ações de combate.");
    }
}
