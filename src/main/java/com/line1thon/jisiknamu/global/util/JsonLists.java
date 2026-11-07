package com.line1thon.jisiknamu.global.util;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * JSON 컬럼(options, grading_points)과 List<String> 변환. null은 null로 둔다.
 */
@Component
@RequiredArgsConstructor
public class JsonLists {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public String toJson(List<String> values) {
        return values == null ? null : objectMapper.writeValueAsString(values);
    }

    public List<String> toList(String json) {
        return json == null ? null : objectMapper.readValue(json, STRING_LIST);
    }
}
