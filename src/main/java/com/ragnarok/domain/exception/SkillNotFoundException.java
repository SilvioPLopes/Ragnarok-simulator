package com.ragnarok.domain.exception;

public class SkillNotFoundException extends GameException {
    public SkillNotFoundException(String skillName, String jobClass) {
        super("Skill " + skillName + " não encontrada para a classe " + jobClass);
    }

    public SkillNotFoundException(String skillName) {
        super("Skill " + skillName + " não encontrada.");
    }
}
