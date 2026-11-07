package com.line1thon.jisiknamu.global.concurrency;

import com.line1thon.jisiknamu.global.error.BusinessException;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 같은 대상에 처리 중인 요청이 있으면 REQUEST_IN_PROGRESS로 돌려보낸다. 서버가 1대라 메모리 잠금으로 충분하다.
 */
@Component
public class InProgressGuard {

    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();

    public <T> T run(String key, Supplier<T> action) {
        if (!inProgress.add(key)) {
            throw new BusinessException(ErrorCode.REQUEST_IN_PROGRESS);
        }
        try {
            return action.get();
        } finally {
            inProgress.remove(key);
        }
    }
}
