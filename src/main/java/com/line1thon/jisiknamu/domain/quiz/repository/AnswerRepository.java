package com.line1thon.jisiknamu.domain.quiz.repository;

import com.line1thon.jisiknamu.domain.quiz.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
}
