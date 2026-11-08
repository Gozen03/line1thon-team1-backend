package com.line1thon.jisiknamu.domain.tree.entity;

import com.line1thon.jisiknamu.domain.user.entity.User;
import com.line1thon.jisiknamu.global.entity.BaseTimeEntity;
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
@Table(name = "trees")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tree extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String subjectName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StudyLevel studyLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TreeShape shape;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BackgroundColor backgroundColor;

    @Column(name = "is_paid", nullable = false)
    private boolean paid;

    @Builder
    private Tree(User user, String subjectName, StudyLevel studyLevel, TreeShape shape, BackgroundColor backgroundColor) {
        this.user = user;
        this.subjectName = subjectName;
        this.studyLevel = studyLevel;
        this.shape = shape;
        this.backgroundColor = backgroundColor == null ? BackgroundColor.BLUE : backgroundColor;
        this.paid = false; // MVP는 결제가 없다
    }

    public void changeBackgroundColor(BackgroundColor backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public void changeSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public void changeStudyLevel(StudyLevel studyLevel) {
        this.studyLevel = studyLevel;
    }
}
