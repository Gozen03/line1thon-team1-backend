package com.line1thon.jisiknamu.domain.tree.dto;

import com.line1thon.jisiknamu.domain.tree.entity.BackgroundColor;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.TreeShape;
import io.swagger.v3.oas.annotations.media.Schema;

public record TreeCreateRequest(
        @Schema(description = "과목 이름. 앞뒤 공백을 지운 뒤 1~20자", example = "물리", requiredMode = Schema.RequiredMode.REQUIRED)
        String subjectName,
        @Schema(description = "공부 수준: 초등학생 / 중학생 / 고등학생 / 대학생 / 개인 공부", example = "HIGH", requiredMode = Schema.RequiredMode.REQUIRED)
        StudyLevel studyLevel,
        @Schema(description = "나무 모양. 지금은 BASIC만 고를 수 있다", example = "BASIC", requiredMode = Schema.RequiredMode.REQUIRED)
        TreeShape treeShape,
        @Schema(description = "배경 색상: 분홍 / 노랑 / 초록 / 하늘. 안 보내면 BLUE", example = "BLUE")
        BackgroundColor backgroundColor) {
}
