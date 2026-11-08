package com.line1thon.jisiknamu.domain.tree.dto;

import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import com.line1thon.jisiknamu.domain.tree.entity.BackgroundColor;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.TreeShape;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record TreeDetailResponse(
        @Schema(description = "나무 ID", example = "1") Long treeId,
        @Schema(description = "과목 이름", example = "수학") String subjectName,
        @Schema(description = "공부 수준", example = "HIGH") StudyLevel studyLevel,
        @Schema(description = "나무 모양", example = "BASIC") TreeShape treeShape,
        @Schema(description = "배경 색상", example = "BLUE") BackgroundColor backgroundColor,
        @Schema(description = "잎눈을 포함한 잎 수", example = "13") long leafCount,
        @Schema(description = "지금 복습할 수 있는 잎 수", example = "3") long reviewPendingCount,
        @Schema(description = "받았지만 제출하지 않은 첫 풀이·재도전 세트 ID. 없으면 null. 값이 있으면 이어 풀기를 묻고 S4를 부른다", example = "301") Long pendingQuizSetId,
        @Schema(description = "풀던 문제의 공부 날짜 (팝업의 \"11월 3일 공부\"). pendingQuizSetId가 없으면 null", example = "2026-11-03T20:00:00+09:00") OffsetDateTime pendingStudiedAt,
        @Schema(description = "잎 목록. position 오름차순") List<LeafItem> leaves) {

    public record LeafItem(
            @Schema(description = "잎 ID", example = "13") Long leafId,
            @Schema(description = "자리 1~24 (공부한 순서). 가지: 1~5 / 6~12 / 13~19 / 20~24", example = "13") int position,
            @Schema(description = "지금 등급: 싱싱한 잎 / 연두잎 / 단풍잎 / 마른 잎 / 잎눈", example = "GREEN") LeafGrade grade,
            @Schema(description = "처음 등급. 복습으로 바뀌면 grade와 다르다", example = "AUTUMN") LeafGrade originalGrade,
            @Schema(description = "공부한 날", example = "2026-10-29T20:00:00+09:00") OffsetDateTime studiedAt,
            @Schema(description = "복습에 성공했는지", example = "true") boolean reviewed,
            @Schema(description = "지금 복습할 수 있는지 (느낌표 표시)", example = "false") boolean reviewAvailable) {
    }
}
