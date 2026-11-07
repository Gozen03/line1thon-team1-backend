package com.line1thon.jisiknamu.global.storage;

import java.util.List;

/**
 * DB에서 지운 사진의 key. 트랜잭션이 커밋된 뒤 저장소에서 지운다.
 */
public record ImagesDeletedEvent(List<String> keys) {
}
