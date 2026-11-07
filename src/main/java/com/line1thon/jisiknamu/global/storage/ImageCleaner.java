package com.line1thon.jisiknamu.global.storage;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 명세: DB를 먼저 지우고 사진은 그 뒤에 지운다. 사진 삭제가 실패해도 로그만 남기고 요청은 성공으로 끝낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageCleaner {

    private final ImageStorage imageStorage;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onImagesDeleted(ImagesDeletedEvent event) {
        deleteQuietly(event.keys());
    }

    public void deleteQuietly(Collection<String> keys) {
        for (String key : keys) {
            try {
                imageStorage.delete(key);
            } catch (RuntimeException e) {
                log.error("사진 삭제 실패: {}", key, e);
            }
        }
    }
}
