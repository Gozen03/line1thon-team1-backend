package com.line1thon.jisiknamu.global.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 한국 시간 처리. 서비스의 현재 시각은 LocalDateTime.now() 대신 now()를 쓴다.
 */
public final class KstTime {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private KstTime() {
    }

    /**
     * DB의 DATETIME이 초 단위라 초까지만 쓴다. 저장 직후 응답과 나중에 DB에서 읽은 값이 같아진다.
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(KST).truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * DB의 DATETIME(LocalDateTime)을 API 응답용 +09:00 시각으로 바꾼다.
     */
    public static OffsetDateTime toOffset(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(KST).toOffsetDateTime();
    }
}
