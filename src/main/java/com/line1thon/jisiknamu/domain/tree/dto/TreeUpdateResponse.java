package com.line1thon.jisiknamu.domain.tree.dto;

import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.TreeShape;
import io.swagger.v3.oas.annotations.media.Schema;

public record TreeUpdateResponse(
        @Schema(description = "나무 ID", example = "1") Long treeId,
        @Schema(description = "과목 이름", example = "고등 수학") String subjectName,
        @Schema(description = "공부 수준", example = "HIGH") StudyLevel studyLevel,
        @Schema(description = "나무 모양. 수정할 수 없다", example = "BASIC") TreeShape treeShape) {
}
