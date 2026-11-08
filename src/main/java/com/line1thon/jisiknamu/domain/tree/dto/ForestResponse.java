package com.line1thon.jisiknamu.domain.tree.dto;

import com.line1thon.jisiknamu.domain.tree.entity.BackgroundColor;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.TreeShape;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record ForestResponse(
        @Schema(description = "무료 나무 한도", example = "3") int freeTreeLimit,
        @Schema(description = "지금 가진 나무 수", example = "3") int treeCount,
        @Schema(description = "무료 나무를 더 심을 수 있는지", example = "false") boolean canAddFreeTree,
        @Schema(description = "나무 목록. 만든 순서") List<TreeCard> trees) {

    public record TreeCard(
            @Schema(description = "나무 ID", example = "1") Long treeId,
            @Schema(description = "과목 이름", example = "수학") String subjectName,
            @Schema(description = "공부 수준", example = "HIGH") StudyLevel studyLevel,
            @Schema(description = "나무 모양", example = "BASIC") TreeShape treeShape,
            @Schema(description = "배경 색상 (카드 배경)", example = "BLUE") BackgroundColor backgroundColor,
            @Schema(description = "잎눈을 포함한 잎 수", example = "13") long leafCount,
            @Schema(description = "마지막 공부 시각. 공부 기록이 없으면 null. \"3일 전\" 표시는 프론트가 계산한다", example = "2026-10-31T21:10:00+09:00") OffsetDateTime lastStudiedAt,
            @Schema(description = "지금 복습할 수 있는 잎 수. 0이면 배지를 숨긴다", example = "3") long reviewPendingCount) {
    }
}
