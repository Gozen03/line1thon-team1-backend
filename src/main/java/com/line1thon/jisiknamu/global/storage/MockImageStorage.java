package com.line1thon.jisiknamu.global.storage;

import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * S3_BUCKET이 비어 있을 때(로컬 개발) 쓰는 가짜 저장소. 파일을 저장하지 않는다.
 */
@Slf4j
@Component
@ConditionalOnExpression("'${s3.bucket:}'.isEmpty()")
public class MockImageStorage implements ImageStorage {

    public MockImageStorage() {
        log.warn("S3_BUCKET이 없어 사진을 저장하지 않는 MockImageStorage를 씁니다.");
    }

    @Override
    public void upload(String key, InputStream content, long size, String contentType) {
    }

    @Override
    public String createReadUrl(String key) {
        return "https://mock-storage.invalid/" + key;
    }

    @Override
    public void delete(String key) {
    }
}
