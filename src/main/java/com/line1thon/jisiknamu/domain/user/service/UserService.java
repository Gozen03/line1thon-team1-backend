package com.line1thon.jisiknamu.domain.user.service;

import com.line1thon.jisiknamu.domain.study.repository.StudyImageRepository;
import com.line1thon.jisiknamu.domain.user.dto.AuthRequest;
import com.line1thon.jisiknamu.domain.user.dto.AuthResponse;
import com.line1thon.jisiknamu.domain.user.dto.MyInfoResponse;
import com.line1thon.jisiknamu.domain.user.dto.NicknameUpdateResponse;
import com.line1thon.jisiknamu.domain.user.entity.User;
import com.line1thon.jisiknamu.domain.user.repository.UserRepository;
import com.line1thon.jisiknamu.global.error.BusinessException;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import com.line1thon.jisiknamu.global.security.JwtProvider;
import com.line1thon.jisiknamu.global.storage.ImagesDeletedEvent;
import com.line1thon.jisiknamu.global.util.KstTime;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int NICKNAME_MIN_LENGTH = 2;
    private static final int NICKNAME_MAX_LENGTH = 10;
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("[0-9]{4,6}");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final StudyImageRepository studyImageRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AuthResponse signUp(AuthRequest request) {
        String nickname = normalizeNickname(request.nickname());
        if (!isValidPassword(request.password())) {
            throw new BusinessException(ErrorCode.PASSWORD_INVALID);
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        User user = User.builder()
                .nickname(nickname)
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 닉네임으로 가입하면 위 확인을 통과해도 UNIQUE 제약에서 걸린다
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest request) {
        // 닉네임이 없든 비밀번호가 틀리든 LOGIN_FAILED 하나로 응답한다
        if (request.nickname() == null || !isValidPassword(request.password())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        User user = userRepository.findByNickname(request.nickname().strip())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public MyInfoResponse getMyInfo(Long userId) {
        User user = getUser(userId);
        return new MyInfoResponse(user.getId(), user.getNickname(), KstTime.toOffset(user.getCreatedAt()));
    }

    @Transactional
    public NicknameUpdateResponse updateNickname(Long userId, String rawNickname) {
        User user = getUser(userId);
        String nickname = normalizeNickname(rawNickname);
        if (userRepository.existsByNicknameAndIdNot(nickname, userId)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        user.changeNickname(nickname);
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        return new NicknameUpdateResponse(user.getId(), user.getNickname());
    }

    /**
     * 나무·공부 기록·문제 세트·잎·광고 횟수는 DB의 ON DELETE CASCADE로 함께 지워진다. 사진은 커밋 뒤에 지운다.
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = getUser(userId);
        List<String> imageKeys = studyImageRepository.findS3KeysByUserId(userId);
        userRepository.delete(user);
        eventPublisher.publishEvent(new ImagesDeletedEvent(imageKeys));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private String normalizeNickname(String rawNickname) {
        if (rawNickname == null) {
            throw new BusinessException(ErrorCode.NICKNAME_INVALID);
        }
        String nickname = rawNickname.strip();
        int length = nickname.codePointCount(0, nickname.length());
        if (length < NICKNAME_MIN_LENGTH || length > NICKNAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.NICKNAME_INVALID);
        }
        return nickname;
    }

    private boolean isValidPassword(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(user.getId(), user.getNickname(), jwtProvider.createToken(user.getId()));
    }
}
