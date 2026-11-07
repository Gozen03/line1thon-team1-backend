package com.line1thon.jisiknamu.domain.leaf.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * R0·R1의 잎 하나. treeId·subjectName은 R0에만 있다 (R1은 응답 위쪽에 있다).
 */
public record ReviewItem(
        @Schema(description = "잎 ID", example = "13") Long leafId,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "나무 ID (R0만)", example = "1") Long treeId,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "나무 과목 이름 (R0만)", example = "수학") String subjectName,
        @Schema(description = "지금 등급", example = "AUTUMN") LeafGrade grade,
        @Schema(description = "공부한 날", example = "2026-10-29T20:00:00+09:00") OffsetDateTime studiedAt,
        @Schema(description = "약한 주제") List<String> weakTopics,
        @Schema(description = "복습 문제 수. 항상 3", example = "3") int questionCount) {
}
