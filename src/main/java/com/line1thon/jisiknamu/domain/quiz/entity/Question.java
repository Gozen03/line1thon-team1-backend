package com.line1thon.jisiknamu.domain.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_set_id", nullable = false)
    private QuizSet quizSet;

    @Column(nullable = false)
    private int number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // JSON 문자열. 변환은 서비스에서 한다
    @Column(columnDefinition = "json")
    private String options;

    private Integer answerIndex;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(columnDefinition = "TEXT")
    private String modelAnswer;

    // JSON 문자열. 변환은 서비스에서 한다
    @Column(columnDefinition = "json")
    private String gradingPoints;

    @Builder
    private Question(QuizSet quizSet, int number, Difficulty difficulty, QuestionType type, String topic,
                     String content, String options, Integer answerIndex, String explanation,
                     String modelAnswer, String gradingPoints) {
        this.quizSet = quizSet;
        this.number = number;
        this.difficulty = difficulty;
        this.type = type;
        this.topic = topic;
        this.content = content;
        this.options = options;
        this.answerIndex = answerIndex;
        this.explanation = explanation;
        this.modelAnswer = modelAnswer;
        this.gradingPoints = gradingPoints;
    }
}
