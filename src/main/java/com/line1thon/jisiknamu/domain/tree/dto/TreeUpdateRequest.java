package com.line1thon.jisiknamu.domain.tree.dto;

import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;

/**
 * 보낸 필드만 바뀐다 (null은 보내지 않은 것으로 본다).
 */
public record TreeUpdateRequest(String subjectName, StudyLevel studyLevel) {
}
