package com.line1thon.jisiknamu.domain.tree.dto;

import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 보낸 필드만 바뀐다 (null은 보내지 않은 것으로 본다).
 */
public record TreeUpdateRequest(
        @Schema(description = "새 과목 이름. 앞뒤 공백을 지운 뒤 1~20자. 안 보내면 그대로", example = "고등 수학")
        String subjectName,
        @Schema(description = "새 공부 수준. 안 보내면 그대로", example = "HIGH")
        StudyLevel studyLevel) {
}
