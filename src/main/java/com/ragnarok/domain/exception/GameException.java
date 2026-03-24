package com.ragnarok.domain.exception;

/**
 * Exceção base para todas as violações de regra de negócio do domínio.
 * Subclasses representam cenários específicos do jogo (SP insuficiente,
 * pré-requisito de skill não atendido, etc.).
 */
public class GameException extends RuntimeException {

    public GameException(String message) {
        super(message);
    }

    public GameException(String message, Throwable cause) {
        super(message, cause);
    }
}
