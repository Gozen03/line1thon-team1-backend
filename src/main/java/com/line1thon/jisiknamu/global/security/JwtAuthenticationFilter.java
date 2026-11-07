package com.line1thon.jisiknamu.global.security;

import com.line1thon.jisiknamu.domain.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer 토큰이 유효하면 userId를 인증 정보로 넣는다. 컨트롤러에서는 @AuthenticationPrincipal Long userId로 받는다.
 * 토큰이 없거나 틀리면 인증 없이 넘겨서, 인증이 필요한 경로는 SecurityErrorHandler가 401로 응답한다.
 * 탈퇴한 회원의 토큰도 401이 되도록 회원 존재를 확인한다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtProvider.parseUserId(header.substring(BEARER_PREFIX.length()))
                    .filter(userRepository::existsById)
                    .ifPresent(userId -> SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(userId, null, List.of())));
        }
        filterChain.doFilter(request, response);
    }
}
