package com.line1thon.jisiknamu.global.ai;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 테스트용 가짜 AI 서버. 정해 둔 응답을 돌려주고 받은 요청을 기록한다.
 */
class FakeAiServer implements AutoCloseable {

    record Received(String method, String path, String internalKey, String contentType, String body) {
    }

    private final HttpServer server;
    private final List<Received> received = new ArrayList<>();
    private volatile int status = 200;
    private volatile String responseBody = "{}";
    private volatile String responseContentType = "application/json";
    private volatile long delayMillis;

    FakeAiServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            synchronized (received) {
                received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("X-Internal-Key"),
                        exchange.getRequestHeaders().getFirst("Content-Type"), body));
            }
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", responseContentType);
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (IOException e) {
                // 클라이언트가 시간 초과로 먼저 끊은 경우
            }
            exchange.close();
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void respond(int status, String body) {
        this.status = status;
        this.responseBody = body;
        this.responseContentType = "application/json";
    }

    void respondSuccess(String dataJson) {
        respond(200, "{\"success\":true,\"data\":" + dataJson + ",\"error\":null}");
    }

    void respondError(int status, String code) {
        respond(status, "{\"success\":false,\"data\":null,\"error\":{\"code\":\"" + code + "\",\"message\":\"m\"}}");
    }

    void respondHtml(int status) {
        this.status = status;
        this.responseBody = "<html><body>502 Bad Gateway</body></html>";
        this.responseContentType = "text/html";
    }

    void delay(long millis) {
        this.delayMillis = millis;
    }

    Received last() {
        synchronized (received) {
            return received.get(received.size() - 1);
        }
    }

    int count() {
        synchronized (received) {
            return received.size();
        }
    }

    // AI1 응답 7문제. 객관식 정답은 number % 4
    static String quizData(String extractedText) {
        StringBuilder questions = new StringBuilder();
        String[] difficulties = {"EASY", "EASY", "NORMAL", "NORMAL", "HARD", "HARD"};
        for (int number = 1; number <= 6; number++) {
            questions.append(multipleChoice(number, difficulties[number - 1], "주제 " + number)).append(',');
        }
        questions.append("""
                {"number":7,"difficulty":"ESSAY","questionType":"ESSAY","topic":"서술 주제","content":"설명해 보세요.",
                 "options":null,"answerIndex":null,"explanation":null,"modelAnswer":"모범답안",
                 "gradingPoints":["포인트 1","포인트 2"]}""");
        String extracted = extractedText == null ? "null" : "\"" + extractedText + "\"";
        return "{\"readable\":true,\"extractedText\":" + extracted + ",\"questions\":[" + questions + "]}";
    }

    static String multipleChoice(int number, String difficulty, String topic) {
        return """
                {"number":%d,"difficulty":"%s","questionType":"MULTIPLE_CHOICE","topic":"%s","content":"%d번 문제",
                 "options":["가","나","다","라"],"answerIndex":%d,"explanation":"해설 %d","modelAnswer":null,
                 "gradingPoints":null,"extraField":"무시"}""".formatted(number, difficulty, topic, number, number % 4, number);
    }

    static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
