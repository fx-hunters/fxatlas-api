plugins {
    java
    jacoco
}

// 모든 서브프로젝트(engine, app) 공통 설정
subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    group = "com.fxatlas"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    // 문서 8장: JaCoCo 라인·브랜치 100%, 보일러플레이트는 측정 대상에서 제외
    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        violationRules {
            rule {
                limit {
                    minimum = "1.00".toBigDecimal()
                }
            }
        }
        classDirectories.setFrom(
            files(classDirectories.files.map {
                fileTree(it) {
                    exclude(
                        "**/dto/**",
                        "**/entity/**",
                        "**/config/**",
                        "**/port/**",          // 포트 인터페이스 + 계약용 값객체(record) — 자체 로직 없음
                        "**/architecture/**",  // 레이어 마커 어노테이션 — 로직 없음
                        "**/*Application.class",
                    )
                }
            })
        )
    }

    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestCoverageVerification"))
    }
}
