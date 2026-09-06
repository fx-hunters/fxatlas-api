plugins {
    java
    jacoco
}

// 문서 8장: JaCoCo 커버리지 측정 제외 대상(보일러플레이트).
// 리포트와 게이트가 같은 대상을 보도록 한 곳에서 정의한다 —
// 리포트에는 잡히는데 게이트에는 안 걸리는(혹은 그 반대인) 클래스가 있으면
// CI 아티팩트로 받은 리포트를 근거로 미달 원인을 찾을 수 없다(이슈 #40).
val coverageExcludes = listOf(
    "**/dto/**",
    "**/entity/**",
    "**/config/**",
    "**/port/**",          // 포트 인터페이스 + 계약용 값객체(record) — 자체 로직 없음
    "**/architecture/**",  // 레이어 마커 어노테이션 — 로직 없음
    "**/*Application.class",
)

// 모든 서브프로젝트(engine, app) 공통 설정
subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    group = "com.divurve"
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

    // 보일러플레이트를 뺀 커버리지 측정 대상. 리포트와 검증이 동일한 집합을 보게 한다.
    fun measuredClassDirs(source: FileCollection): FileCollection =
        files(source.files.map { dir -> fileTree(dir) { exclude(coverageExcludes) } })

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            // XML: CI 아티팩트를 외부 도구(커버리지 배지·리뷰 코멘트)가 파싱할 수 있게 한다.
            xml.required.set(true)
            // HTML: 사람이 미커버 라인을 바로 짚을 수 있는 형태. CI 아티팩트의 주 용도.
            html.required.set(true)
        }
        classDirectories.setFrom(measuredClassDirs(classDirectories))
    }

    // 문서 8장: 라인·브랜치 100%.
    // counter 를 명시하지 않으면 JaCoCo 기본값인 INSTRUCTION 만 검증한다 —
    // 문서가 요구하는 LINE·BRANCH 는 검사되지 않아, 미커버 분기가 있어도 게이트를 통과했다(이슈 #40).
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        violationRules {
            rule {
                limit {
                    counter = "INSTRUCTION"
                    minimum = "1.00".toBigDecimal()
                }
                limit {
                    counter = "LINE"
                    minimum = "1.00".toBigDecimal()
                }
                limit {
                    counter = "BRANCH"
                    minimum = "1.00".toBigDecimal()
                }
            }
        }
        classDirectories.setFrom(measuredClassDirs(classDirectories))
    }

    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestCoverageVerification"))
    }
}

/**
 * CI(.github/workflows/ci.yml)와 동일한 검증을 로컬에서 한 번에 돌린다.
 *
 * 로컬 Docker(29.x)의 API 버전이 Testcontainers 기본값보다 높아 컨테이너 기동이 실패하므로,
 * 실행 전에 `export DOCKER_API_VERSION=1.44` 가 필요하다(README 참고).
 */
tasks.register("ciCheck") {
    group = "verification"
    description = "CI 와 동일한 검증(build + test + 커버리지 + 아키텍처)을 로컬에서 실행한다"
    dependsOn(
        subprojects.map { it.tasks.named("build") },
        subprojects.map { it.tasks.named("test") },
        subprojects.map { it.tasks.named("jacocoTestCoverageVerification") },
    )
}
