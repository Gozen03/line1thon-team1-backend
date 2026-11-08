package com.line1thon.jisiknamu.domain.tree.dto;

import com.line1thon.jisiknamu.domain.tree.entity.BackgroundColor;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.TreeShape;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record TreeCreateResponse(
        @Schema(description = "나무 ID", example = "4") Long treeId,
        @Schema(description = "과목 이름", example = "물리") String subjectName,
        @Schema(description = "공부 수준", example = "HIGH") StudyLevel studyLevel,
        @Schema(description = "나무 모양", example = "BASIC") TreeShape treeShape,
        @Schema(description = "배경 색상", example = "BLUE") BackgroundColor backgroundColor,
        @Schema(description = "잎 수. 새 나무는 0", example = "0") long leafCount,
        @Schema(description = "만든 날", example = "2026-11-03T11:30:00+09:00") OffsetDateTime createdAt) {
}
