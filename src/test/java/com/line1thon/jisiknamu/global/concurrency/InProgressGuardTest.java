package com.line1thon.jisiknamu.global.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.line1thon.jisiknamu.global.error.BusinessException;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

class InProgressGuardTest {

    private final InProgressGuard guard = new InProgressGuard();

    @Test
    void 같은_대상이_처리_중이면_REQUEST_IN_PROGRESS() {
        guard.run("tree:1", () -> {
            assertThatThrownBy(() -> guard.run("tree:1", () -> "두 번째"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_IN_PROGRESS));
            return "첫 번째";
        });
    }

    @Test
    void 다른_대상은_함께_처리한다() {
        String result = guard.run("tree:1", () -> guard.run("tree:2", () -> "완료"));
        assertThat(result).isEqualTo("완료");
    }

    @Test
    void 끝나면_다시_받고_예외가_나도_풀린다() {
        guard.run("tree:1", () -> "완료");
        assertThatThrownBy(() -> guard.run("tree:1", () -> {
            throw new IllegalStateException("실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(guard.run("tree:1", () -> "다시")).isEqualTo("다시");
    }
}
