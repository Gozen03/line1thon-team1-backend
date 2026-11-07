package com.line1thon.jisiknamu.domain.study.dto;

import com.line1thon.jisiknamu.domain.study.entity.InputType;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public record StudyCreateRequest(InputType inputType, List<MultipartFile> images, String content, String memo) {
}
