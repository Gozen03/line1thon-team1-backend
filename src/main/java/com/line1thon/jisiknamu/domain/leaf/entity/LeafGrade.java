package com.line1thon.jisiknamu.domain.leaf.entity;

import java.util.Set;

/** 잎 등급 */
public enum LeafGrade {
    GREEN, LIGHT_GREEN, AUTUMN, BROWN, BUD;

    /** 복습 대상 등급: 단풍잎·갈색잎 */
    public static final Set<LeafGrade> REVIEWABLE = Set.of(AUTUMN, BROWN);
}
