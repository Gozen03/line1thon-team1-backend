package com.line1thon.jisiknamu.domain.leaf.dto;

import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import com.line1thon.jisiknamu.domain.quiz.entity.QuestionType;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import com.line1thon.jisiknamu.domain.study.entity.InputType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record LeafDetailResponse(
        @Schema(description = "잎 ID", example = "13") Long leafId,
        @Schema(description = "나무 ID", example = "1") Long treeId,
        @Schema(description = "공부할 때의 과목 이름", example = "수학") String subjectName,
        @Schema(description = "자리 1~24", example = "13") int position,
        @Schema(description = "지금 등급", example = "GREEN") LeafGrade grade,
        @Schema(description = "처음 등급. 복습으로 바뀌면 grade와 다르다", example = "AUTUMN") LeafGrade originalGrade,
        @Schema(description = "등급을 정한 풀이의 맞힌 개수", example = "3") int correctCount,
        @Schema(description = "공부한 날", example = "2026-10-29T20:00:00+09:00") OffsetDateTime studiedAt,
        @Schema(description = "복습에 성공했는지", example = "true") boolean reviewed,
        @Schema(description = "복습 성공 시각. 아니면 null", example = "2026-11-01T19:40:00+09:00") OffsetDateTime reviewedAt,
        @Schema(description = "그날 입력한 공부 내용") StudyInfo study,
        @Schema(description = "등급을 정한 마지막 첫 풀이·재도전 세트의 7문제. 틀린 문제를 먼저 2개 보여 주고 \"7문제 모두 보기\"로 펼친다") List<QuestionItem> questions,
        @Schema(description = "피드백") Feedback feedback,
        @Schema(description = "이 잎에 쌓인 제출 완료 세트 기록") List<QuizSetItem> quizSets) {

    public record StudyInfo(
            @Schema(description = "입력 방식", example = "IMAGE") InputType inputType,
            @Schema(description = "노트 사진 주소. 10분 뒤 만료되니 저장하지 말고 화면을 열 때마다 받은 주소를 쓴다") List<String> imageUrls,
            @Schema(description = "입력한 텍스트. 사진이면 null") String content,
            @Schema(description = "메모. 없으면 null", example = "이차함수 꼭짓점, 판별식으로 x절편 개수 구하기") String memo) {
    }

    public record QuestionItem(
            @Schema(description = "문제 번호", example = "4") int number,
            @Schema(description = "문제 주제", example = "판별식과 x절편 개수") String topic,
            @Schema(description = "문제 형식", example = "MULTIPLE_CHOICE") QuestionType questionType,
            @Schema(description = "문제 본문", example = "y = x² − 2x + 5 의 x절편 개수는?") String content,
            @Schema(description = "보기 4개. 서술형은 null") List<String> options,
            @Schema(description = "내가 고른 번호. 서술형이거나 안 풀었으면 null", example = "2") Integer selectedIndex,
            @Schema(description = "정답 번호. 서술형은 null", example = "0") Integer answerIndex,
            @Schema(description = "정답 여부. 서술형은 null", example = "false") Boolean isCorrect,
            @Schema(description = "해설. 서술형은 null", example = "판별식 D = −16 < 0") String explanation,
            @Schema(description = "서술형 답. 객관식은 null") String essayText,
            @Schema(description = "서술형 모범답안. 객관식은 null") String modelAnswer) {
    }

    public record Feedback(
            @Schema(description = "총평. 복습에 성공했으면 복습 결과까지 반영한 총평") String summary,
            @Schema(description = "잘한 주제") List<String> strengths,
            @Schema(description = "약했던 주제. 복습으로 이해한 주제도 기록으로 남는다") List<String> weaknesses,
            @Schema(description = "서술형 AI 피드백") String essayFeedback) {
    }

    public record QuizSetItem(
            @Schema(description = "문제 세트 ID", example = "301") Long quizSetId,
            @Schema(description = "세트 종류", example = "INITIAL") QuizSetType quizSetType,
            @Schema(description = "맞힌 개수", example = "3") Integer correctCount,
            @Schema(description = "제출 시각", example = "2026-10-29T21:10:00+09:00") OffsetDateTime submittedAt) {
    }
}
