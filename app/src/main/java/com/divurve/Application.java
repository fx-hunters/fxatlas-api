package com.divurve;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Divurve 백엔드 진입점.
 * 아키텍처 테스트(Layer/Module ArchitectureTest)는 app/src/test 아래에 둔다.
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
