package com.line1thon.jisiknamu.global.ai;

import com.line1thon.jisiknamu.domain.study.entity.InputType;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import java.util.List;

/**
 * API 명세서 11장의 문제 출제 요청. 사진이면 imageUrls(Presigned URL), 텍스트면 content를 쓴다.
 */
public record QuizGenerateRequest(String subjectName, StudyLevel studyLevel, InputType inputType,
                                  List<String> imageUrls, String content, String memo, List<String> weakTopics) {
}
