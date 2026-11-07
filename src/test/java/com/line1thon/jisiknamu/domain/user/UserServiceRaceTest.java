package com.line1thon.jisiknamu.domain.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.line1thon.jisiknamu.domain.user.dto.AuthRequest;
import com.line1thon.jisiknamu.domain.user.entity.User;
import com.line1thon.jisiknamu.domain.user.repository.UserRepository;
import com.line1thon.jisiknamu.domain.user.service.UserService;
import com.line1thon.jisiknamu.global.error.BusinessException;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import com.line1thon.jisiknamu.global.security.JwtProvider;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

// 동시 요청이 중복 확인을 함께 통과해 UNIQUE 제약에 걸리는 경우. 실제로 재현하기 어려워 Mock으로 확인한다
class UserServiceRaceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserService userService = new UserService(
            userRepository, mock(PasswordEncoder.class), mock(JwtProvider.class));

    @Test
    void 가입이_UNIQUE_제약에_걸리면_NICKNAME_DUPLICATED() {
        when(userRepository.existsByNickname("테스트닉")).thenReturn(false);
        when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertDuplicated(() -> userService.signUp(new AuthRequest("테스트닉", "1234")));
    }

    @Test
    void 닉네임_수정이_UNIQUE_제약에_걸리면_NICKNAME_DUPLICATED() {
        User user = User.builder().nickname("예전닉").passwordHash("hash").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByNicknameAndIdNot("테스트닉", 1L)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate")).when(userRepository).flush();

        assertDuplicated(() -> userService.updateNickname(1L, "테스트닉"));
    }

    private void assertDuplicated(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NICKNAME_DUPLICATED));
    }
}
