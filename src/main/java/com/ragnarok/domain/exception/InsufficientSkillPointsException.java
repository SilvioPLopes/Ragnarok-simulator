package com.ragnarok.domain.exception;

public class InsufficientSkillPointsException extends GameException {
    public InsufficientSkillPointsException() {
        super("Sem Skill Points disponíveis.");
    }
}
