package com.line1thon.jisiknamu.domain.quiz.repository;

import com.line1thon.jisiknamu.domain.quiz.entity.Question;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findAllByQuizSetIdOrderByNumberAsc(Long quizSetId);
}
