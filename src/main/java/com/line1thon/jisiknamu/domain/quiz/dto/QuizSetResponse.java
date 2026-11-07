package com.line1thon.jisiknamu.domain.quiz.dto;

import com.line1thon.jisiknamu.domain.quiz.entity.Difficulty;
import com.line1thon.jisiknamu.domain.quiz.entity.QuestionType;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * S1·S3·S4 공통 응답. 정답·해설·모범답안은 넣지 않는다.
 */
public record QuizSetResponse(
        @Schema(description = "공부 기록 ID", example = "31") Long studyId,
        @Schema(description = "문제 세트 ID. 답 제출과 S4에 쓴다", example = "301") Long quizSetId,
        @Schema(description = "세트 종류: 첫 풀이 / 꽃봉오리 재도전 / 복습", example = "INITIAL") QuizSetType quizSetType,
        @Schema(description = "나무 ID", example = "1") Long treeId,
        @Schema(description = "공부할 때의 과목 이름", example = "수학") String subjectName,
        @Schema(description = "공부할 때의 공부 수준", example = "HIGH") StudyLevel studyLevel,
        @Schema(description = "문제 목록. number 순서") List<QuestionItem> questions) {

    public record QuestionItem(
            @Schema(description = "문제 ID", example = "201") Long questionId,
            @Schema(description = "문제 번호. 첫 풀이는 1~7", example = "3") int number,
            @Schema(description = "난이도: 쉬움 1·2번, 보통 3·4번, 어려움 5·6번, 서술형 7번", example = "NORMAL") Difficulty difficulty,
            @Schema(description = "문제 형식: 객관식(4지선다) / 서술형", example = "MULTIPLE_CHOICE") QuestionType questionType,
            @Schema(description = "문제 주제", example = "꼭짓점 좌표 구하기") String topic,
            @Schema(description = "문제 본문", example = "이차함수 y = x² − 4x + 3 의 꼭짓점 좌표는?") String content,
            @Schema(description = "객관식 보기 4개. 서술형은 null", example = "[\"(2, −1)\", \"(−2, 1)\", \"(2, 1)\", \"(4, 3)\"]") List<String> options) {
    }
}
