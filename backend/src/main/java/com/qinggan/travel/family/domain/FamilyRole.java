package com.qinggan.travel.family.domain;

public enum FamilyRole {
    FATHER("爸爸"),
    MOTHER("妈妈"),
    OLDER_SISTER("姐姐"),
    YOUNGER_BROTHER("弟弟"),
    GRANDFATHER("爷爷"),
    GRANDMOTHER("奶奶");

    private final String displayName;

    FamilyRole(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
