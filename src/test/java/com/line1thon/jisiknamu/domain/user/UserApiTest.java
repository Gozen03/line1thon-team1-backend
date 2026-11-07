package com.line1thon.jisiknamu.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.Tree;
import com.line1thon.jisiknamu.domain.tree.entity.TreeShape;
import com.line1thon.jisiknamu.domain.user.entity.User;
import com.line1thon.jisiknamu.global.security.JwtProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// 실제 Security 필터와 MySQL까지 거쳐 확인한다. 트랜잭션은 테스트 뒤 롤백된다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserApiTest {

    private static final String USERS = "/api/v1/users";
    private static final String ME = "/api/v1/users/me";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String LOGOUT = "/api/v1/auth/logout";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Value("${jwt.secret}")
    private String jwtSecret;

    // U1 가입

    @Test
    void 가입하면_201과_토큰을_주고_비밀번호는_해시로_저장한다() throws Exception {
        String body = postJson(USERS, auth("테스트닉", "1234"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("테스트닉"))
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        Number userId = JsonPath.read(body, "$.data.userId");
        User saved = em.find(User.class, userId.longValue());
        assertThat(saved.getPasswordHash()).isNotEqualTo("1234");
        assertThat(passwordEncoder.matches("1234", saved.getPasswordHash())).isTrue();
    }

    @Test
    void 닉네임은_앞뒤_공백을_지우고_저장한다() throws Exception {
        postJson(USERS, auth("  테스트닉  ", "1234"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nickname").value("테스트닉"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "   가   ", "가나다라마바사아자차카", ""})
    void 닉네임_길이를_어기면_NICKNAME_INVALID(String nickname) throws Exception {
        expectError(postJson(USERS, auth(nickname, "1234")), 400, "NICKNAME_INVALID");
    }

    @Test
    void 닉네임이_없으면_NICKNAME_INVALID() throws Exception {
        expectError(postJson(USERS, "{\"password\":\"1234\"}"), 400, "NICKNAME_INVALID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "1234567", "12a4", "١٢٣٤", ""})
    void 비밀번호가_숫자_4에서_6자리가_아니면_PASSWORD_INVALID(String password) throws Exception {
        expectError(postJson(USERS, auth("테스트닉", password)), 400, "PASSWORD_INVALID");
    }

    @Test
    void 비밀번호가_없으면_PASSWORD_INVALID() throws Exception {
        expectError(postJson(USERS, "{\"nickname\":\"테스트닉\"}"), 400, "PASSWORD_INVALID");
    }

    @Test
    void 이미_쓰는_닉네임으로_가입하면_NICKNAME_DUPLICATED() throws Exception {
        signUp("테스트닉");
        expectError(postJson(USERS, auth("테스트닉", "5678")), 409, "NICKNAME_DUPLICATED");
    }

    // U2 로그인

    @Test
    void 로그인하면_토큰을_주고_그_토큰으로_내_정보를_본다() throws Exception {
        signUp("테스트닉");
        String body = postJson(LOGIN, auth("테스트닉", "1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("테스트닉"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.data.accessToken");

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void 비밀번호가_틀리거나_닉네임이_없으면_같은_LOGIN_FAILED() throws Exception {
        signUp("테스트닉");
        expectError(postJson(LOGIN, auth("테스트닉", "9999")), 401, "LOGIN_FAILED");
        expectError(postJson(LOGIN, auth("없는닉네임", "1234")), 401, "LOGIN_FAILED");
        expectError(postJson(LOGIN, "{}"), 401, "LOGIN_FAILED");
    }

    // U3 내 정보

    @Test
    void 내_정보는_한국_시간으로_가입일을_준다() throws Exception {
        String token = signUp("테스트닉");
        em.flush();
        em.clear();

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("테스트닉"))
                .andExpect(jsonPath("$.data.createdAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+09:00")));
    }

    @Test
    void 토큰이_없으면_공통_형식의_401() throws Exception {
        expectError(mockMvc.perform(get(ME)), 401, "UNAUTHORIZED");
    }

    @Test
    void 잘못되거나_만료된_토큰은_401() throws Exception {
        String valid = signUp("테스트닉");
        Long userId = jwtProvider.parseUserId(valid).orElseThrow();
        String expired = new JwtProvider(jwtSecret, -1).createToken(userId);

        expectError(mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer("abc.def.ghi"))), 401, "UNAUTHORIZED");
        expectError(mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(valid + "x"))), 401, "UNAUTHORIZED");
        expectError(mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(expired))), 401, "UNAUTHORIZED");
    }

    // U4 닉네임 수정

    @Test
    void 닉네임을_바꾸면_같은_토큰을_계속_쓰고_새_이름으로_로그인한다() throws Exception {
        String token = signUp("테스트닉");

        patchNickname(token, " 새닉네임 ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.data.userId").isNumber());

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"));
        postJson(LOGIN, auth("새닉네임", "1234")).andExpect(status().isOk());
        expectError(postJson(LOGIN, auth("테스트닉", "1234")), 401, "LOGIN_FAILED");
    }

    @Test
    void 지금_내_닉네임으로_수정하는_것은_중복이_아니다() throws Exception {
        String token = signUp("테스트닉");
        patchNickname(token, "테스트닉").andExpect(status().isOk());
    }

    @Test
    void 닉네임_수정_에러() throws Exception {
        String token = signUp("테스트닉");
        signUp("다른사람");

        expectError(patchNickname(token, "다른사람"), 409, "NICKNAME_DUPLICATED");
        expectError(patchNickname(token, "가"), 400, "NICKNAME_INVALID");
        expectError(mockMvc.perform(patch(ME).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{깨진 JSON")), 400, "INVALID_REQUEST");
    }

    // U5 로그아웃

    @Test
    void 로그아웃은_본문_없는_204() throws Exception {
        String token = signUp("테스트닉");
        mockMvc.perform(post(LOGOUT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void 로그아웃도_토큰이_없으면_401() throws Exception {
        expectError(mockMvc.perform(post(LOGOUT)), 401, "UNAUTHORIZED");
    }

    // U6 회원 탈퇴

    @Test
    void 탈퇴하면_나무도_지워지고_토큰은_401이_되고_닉네임을_다시_쓸_수_있다() throws Exception {
        String token = signUp("테스트닉");
        Long userId = jwtProvider.parseUserId(token).orElseThrow();
        Tree tree = Tree.builder().user(em.find(User.class, userId)).subjectName("수학")
                .studyLevel(StudyLevel.HIGH).shape(TreeShape.BASIC).build();
        em.persist(tree);
        em.flush();
        em.clear(); // 실제 요청처럼 나무가 메모리에 없는 상태에서 지운다

        mockMvc.perform(delete(ME).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        em.flush();
        em.clear();

        assertThat(em.find(User.class, userId)).isNull();
        assertThat(em.find(Tree.class, tree.getId())).isNull();
        expectError(mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(token))), 401, "UNAUTHORIZED");
        postJson(USERS, auth("테스트닉", "5678")).andExpect(status().isCreated());
    }

    // Security와 함께 동작하는 설정

    @Test
    void 허용한_프론트_주소의_preflight는_토큰_없이_통과한다() throws Exception {
        mockMvc.perform(options(ME)
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void 프론트가_401을_읽을_수_있도록_에러_응답에도_CORS_헤더가_붙는다() throws Exception {
        mockMvc.perform(get(ME).header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void Swagger는_토큰_없이_볼_수_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    private String signUp(String nickname) throws Exception {
        String body = postJson(USERS, auth(nickname, "1234"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions patchNickname(String token, String nickname) throws Exception {
        return mockMvc.perform(patch(ME).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"%s\"}".formatted(nickname)));
    }

    private void expectError(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").isString());
    }

    private String auth(String nickname, String password) {
        return "{\"nickname\":\"%s\",\"password\":\"%s\"}".formatted(nickname, password);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
