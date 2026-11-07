package com.line1thon.jisiknamu.domain.leaf.entity;

import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 잎 등급 */
@Getter
@RequiredArgsConstructor
public enum LeafGrade {
    GREEN("완벽하게 이해했어요"),
    LIGHT_GREEN("잘 이해했어요"),
    AUTUMN("조금 더 다지면 초록잎이 돼요"),
    BROWN("다시 공부가 필요해요"),
    BUD("기록은 남기고 재도전");

    /** 복습 대상 등급: 단풍잎·갈색잎 */
    public static final Set<LeafGrade> REVIEWABLE = Set.of(AUTUMN, BROWN);

    private final String message;

    /** 서술형을 뺀 6문제의 맞힌 개수로 등급을 정한다. */
    public static LeafGrade fromCorrectCount(int correctCount) {
        return switch (correctCount) {
            case 6 -> GREEN;
            case 5, 4 -> LIGHT_GREEN;
            case 3, 2 -> AUTUMN;
            case 1 -> BROWN;
            default -> BUD;
        };
    }
}
