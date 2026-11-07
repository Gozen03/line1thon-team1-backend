package com.line1thon.jisiknamu.domain.tree.dto;

import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.TreeShape;

public record TreeCreateRequest(String subjectName, StudyLevel studyLevel, TreeShape treeShape) {
}
