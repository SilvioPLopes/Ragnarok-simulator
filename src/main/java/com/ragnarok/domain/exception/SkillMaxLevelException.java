package com.ragnarok.domain.exception;

public class SkillMaxLevelException extends GameException {
    public SkillMaxLevelException(String skillName, int maxLevel) {
        super("Skill " + skillName + " já está no nível máximo (" + maxLevel + ").");
    }
}
