plugins {
    `java-library`
}

// engine 모듈: 순수 계산 로직. Spring·JPA 등 프레임워크 의존을 절대 추가하지 않는다.
// (app/src/test 의 LayerArchitectureTest 가 이 규칙을 강제한다)
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
