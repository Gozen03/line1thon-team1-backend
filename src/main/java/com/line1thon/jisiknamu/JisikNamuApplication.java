package com.line1thon.jisiknamu;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JisikNamuApplication {

    public static void main(String[] args) {
        // 명세: 날짜·시간은 한국 시간 기준. EC2(UTC)에서도 같게 동작하도록 고정한다.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(JisikNamuApplication.class, args);
    }
}
