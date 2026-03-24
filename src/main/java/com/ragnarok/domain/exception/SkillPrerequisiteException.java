package com.ragnarok.domain.exception;

public class SkillPrerequisiteException extends GameException {
    public SkillPrerequisiteException(String prereqSkill, int prereqLevel) {
        super("Requer " + prereqSkill + " Lv" + prereqLevel);
    }
}
