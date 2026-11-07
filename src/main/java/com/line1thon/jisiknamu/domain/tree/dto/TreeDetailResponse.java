package com.line1thon.jisiknamu.domain.tree.dto;

import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.TreeShape;
import java.time.OffsetDateTime;
import java.util.List;

public record TreeDetailResponse(Long treeId, String subjectName, StudyLevel studyLevel, TreeShape treeShape,
                                 long leafCount, long reviewPendingCount, Long pendingQuizSetId,
                                 List<LeafItem> leaves) {

    public record LeafItem(Long leafId, int position, LeafGrade grade, LeafGrade originalGrade,
                           OffsetDateTime studiedAt, boolean reviewed, boolean reviewAvailable) {
    }
}
