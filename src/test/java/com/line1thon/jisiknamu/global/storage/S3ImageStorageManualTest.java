package com.line1thon.jisiknamu.global.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// S3_TEST_BUCKET을 줄 때만 돈다. S3 호환 서버나 실제 버킷으로 업로드·Presigned URL·삭제를 확인한다.
// 필요한 값: S3_TEST_BUCKET, S3_TEST_ACCESS_KEY, S3_TEST_SECRET_KEY, (호환 서버면) S3_TEST_ENDPOINT
@EnabledIfEnvironmentVariable(named = "S3_TEST_BUCKET", matches = ".+")
class S3ImageStorageManualTest {

    @Test
    void 올린_사진을_Presigned_URL로_읽고_지우면_읽을_수_없다() throws Exception {
        String endpoint = System.getenv("S3_TEST_ENDPOINT");
        S3ImageStorage storage = new S3ImageStorage(System.getenv("S3_TEST_BUCKET"), endpoint == null ? "" : endpoint, 10,
                "ap-northeast-2", System.getenv("S3_TEST_ACCESS_KEY"), System.getenv("S3_TEST_SECRET_KEY"));
        String key = "manual-test/" + UUID.randomUUID() + ".jpg";
        byte[] content = {1, 2, 3, 4};

        storage.upload(key, new ByteArrayInputStream(content), content.length, "image/jpeg");

        String url = storage.createReadUrl(key);
        HttpResponse<byte[]> uploaded = get(url);
        assertThat(uploaded.statusCode()).isEqualTo(200);
        assertThat(uploaded.body()).isEqualTo(content);
        assertThat(uploaded.headers().firstValue("Content-Type")).hasValue("image/jpeg");

        HttpResponse<byte[]> tampered = get(url.replaceFirst("X-Amz-Signature=[0-9a-f]{4}", "X-Amz-Signature=0000"));
        assertThat(tampered.statusCode()).isEqualTo(403);

        storage.delete(key);
        // ListBucket 권한이 없으면 실제 S3는 없는 파일에 404 대신 403을 준다
        assertThat(get(url).statusCode()).isIn(403, 404);
    }

    private HttpResponse<byte[]> get(String url) throws Exception {
        return HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
