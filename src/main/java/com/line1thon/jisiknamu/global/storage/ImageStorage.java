package com.line1thon.jisiknamu.global.storage;

import java.io.InputStream;

/**
 * 노트 사진 저장소. DB에는 key만 저장한다.
 */
public interface ImageStorage {

    void upload(String key, InputStream content, long size, String contentType);

    /**
     * 유효 시간이 정해진 읽기 전용 주소를 돌려준다.
     */
    String createReadUrl(String key);

    void delete(String key);
}
