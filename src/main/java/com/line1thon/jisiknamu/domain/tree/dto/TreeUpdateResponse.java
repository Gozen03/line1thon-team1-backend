package com.line1thon.jisiknamu.domain.tree.dto;

import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.TreeShape;

public record TreeUpdateResponse(Long treeId, String subjectName, StudyLevel studyLevel, TreeShape treeShape) {
}
