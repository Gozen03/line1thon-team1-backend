package com.line1thon.jisiknamu.domain.tree.dto;

import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.TreeShape;
import java.time.OffsetDateTime;

public record TreeCreateResponse(Long treeId, String subjectName, StudyLevel studyLevel, TreeShape treeShape,
                                 long leafCount, OffsetDateTime createdAt) {
}
