package com.line1thon.jisiknamu.domain.tree.dto;

import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.TreeShape;
import java.time.OffsetDateTime;
import java.util.List;

public record ForestResponse(int freeTreeLimit, int treeCount, boolean canAddFreeTree, List<TreeCard> trees) {

    public record TreeCard(Long treeId, String subjectName, StudyLevel studyLevel, TreeShape treeShape,
                           long leafCount, OffsetDateTime lastStudiedAt, long reviewPendingCount) {
    }
}
