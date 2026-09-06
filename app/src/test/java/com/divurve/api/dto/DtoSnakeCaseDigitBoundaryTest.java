package com.divurve.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 회귀 방지 — {@code api/dto} 의 응답·요청 필드가 전역 SNAKE_CASE 전략의 사각지대(숫자 경계)에
 * 걸려 명세와 다른 키로 나가는 것을 막는다 (이슈 #60).
 *
 * <p>Jackson 전역 {@code PropertyNamingStrategies.SNAKE_CASE} 는 <b>대문자 앞에만</b> {@code _} 를
 * 넣고 숫자 경계는 인식하지 못한다 — {@code interval80} 은 {@code interval_80} 이 아니라
 * {@code interval80} 그대로 나간다. 사람이 필드마다 {@code @JsonProperty} 를 빠짐없이 붙이는 것에
 * 의존하면 다음에 또 새는 필드가 생기므로, 실제 운영과 동일하게 구성한 {@link ObjectMapper} 로
 * {@code api/dto} 의 모든 record 를 직렬화해 결과 키를 재귀로 훑는다.
 *
 * <p>레코드 본문에 쓸 더미 값은 리플렉션으로 생성한다({@link #dummyValue}) — 데이터 내용이 아니라
 * <b>구조(필드 이름)</b> 만 검증하면 되기 때문이다. {@code Map} 은 키를 직접 채우지 않고 항상
 * {@code "sample_key"} 하나만 넣는다 — {@code answers: {"q1": "B"}} 같은 <b>사용자 데이터 값</b>은
 * Jackson 명명 전략이 건드리지 않는 영역이라 이 검사 대상이 아니다(문항 코드 {@code q1}~{@code q6} 은
 * 명세가 그대로 쓰는 키이지 Java 필드가 아니다).
 *
 * <p><b>위반 판정 기준</b>: 알파벳 2자 이상이 밑줄 없이 곧장 숫자로 이어지는 자리(예:
 * {@code coverage80}·{@code interval80}·{@code sensitivity1})만 위반으로 본다. 명세는
 * {@code p50_lo}·{@code p80_hi}·{@code vol_30d}·{@code vol_percentile_5y}·{@code per_1pct_krw}
 * 처럼 <b>단일 문자 접두(p·q) + 숫자</b>, <b>숫자 + 단위/코드 접미(d·y·pct)</b> 조합은 그대로 붙여
 * 쓴다 — 통계·설문 코드의 관용 표기이며 Jackson 이 실제로 놓치는 자리(다음절 단어 → 숫자)와는
 * 다르다. 이 조합까지 전부 밑줄로 쪼개면 명세 자체와 어긋난다.
 */
class DtoSnakeCaseDigitBoundaryTest {

    /** 알파벳 2자 이상 뒤에 밑줄 없이 숫자가 곧장 붙는 자리 — CLAUDE.md 5장 숫자 경계 규약 위반. */
    private static final Pattern DIGIT_BOUNDARY_VIOLATION = Pattern.compile("[a-z]{2,}[0-9]");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void dto_직렬화_키에_숫자_경계_스네이크케이스_누락이_없다() throws Exception {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.divurve.api.dto");

        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            Class<?> reflected = javaClass.reflect();
            if (!reflected.isRecord()) {
                continue;
            }
            Object instance = dummyValue(reflected, new ArrayDeque<>());
            JsonNode json = objectMapper.valueToTree(instance);
            collectViolations(reflected.getName(), "$", json, violations);
        }

        assertThat(violations)
                .as("""
                        아래 DTO 는 직렬화 키에 다음절 단어 바로 뒤 밑줄 없는 숫자가 남아있다. \
                        @JsonProperty 로 명세의 키를 고정해라 (CLAUDE.md 5장, 이슈 #60).""")
                .isEmpty();
    }

    private void collectViolations(
            String origin, String path, JsonNode node, List<String> violations) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                String childPath = path + "." + key;
                if (DIGIT_BOUNDARY_VIOLATION.matcher(key).find()) {
                    violations.add(origin + " → " + childPath);
                }
                collectViolations(origin, childPath, entry.getValue(), violations);
            });
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectViolations(origin, path + "[" + i + "]", node.get(i), violations);
            }
        }
    }

    // --- 더미 인스턴스 생성 — 값의 내용이 아니라 record 구조(필드 이름)를 드러내기 위한 용도 ---

    private static Object dummyValue(Type type, Deque<Class<?>> inProgress) {
        if (type instanceof ParameterizedType parameterized) {
            return dummyParameterizedValue(parameterized, inProgress);
        }
        return dummyClassValue((Class<?>) type, inProgress);
    }

    private static Object dummyParameterizedValue(
            ParameterizedType parameterized, Deque<Class<?>> inProgress) {
        Class<?> raw = (Class<?>) parameterized.getRawType();
        Type[] typeArguments = parameterized.getActualTypeArguments();

        if (Map.class.isAssignableFrom(raw)) {
            Map<Object, Object> map = new LinkedHashMap<>();
            map.put("sample_key", dummyValue(typeArguments[1], inProgress));
            return map;
        }
        if (Collection.class.isAssignableFrom(raw)) {
            return List.of(dummyValue(typeArguments[0], inProgress));
        }
        throw new IllegalStateException("더미 값을 만들 수 없는 제네릭 타입: " + parameterized);
    }

    private static Object dummyClassValue(Class<?> type, Deque<Class<?>> inProgress) {
        if (type == String.class || type == Object.class) {
            return "sample";
        }
        if (type == int.class || type == Integer.class) {
            return 1;
        }
        if (type == long.class || type == Long.class) {
            return 1L;
        }
        if (type == short.class || type == Short.class) {
            return (short) 1;
        }
        if (type == byte.class || type == Byte.class) {
            return (byte) 1;
        }
        if (type == double.class || type == Double.class) {
            return 1.0;
        }
        if (type == float.class || type == Float.class) {
            return 1.0f;
        }
        if (type == boolean.class || type == Boolean.class) {
            return true;
        }
        if (type == BigDecimal.class) {
            return BigDecimal.ONE;
        }
        if (type == UUID.class) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        if (type == LocalDate.class) {
            return LocalDate.of(2026, 1, 1);
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.of(2026, 1, 1, 0, 0);
        }
        if (type == Instant.class) {
            return Instant.parse("2026-01-01T00:00:00Z");
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[0];
        }
        if (type.isRecord()) {
            return dummyRecordValue(type, inProgress);
        }
        throw new IllegalStateException("더미 값을 만들 수 없는 타입: " + type);
    }

    private static Object dummyRecordValue(Class<?> recordType, Deque<Class<?>> inProgress) {
        if (inProgress.contains(recordType)) {
            throw new IllegalStateException("record 순환 참조 감지: " + inProgress + " -> " + recordType);
        }
        inProgress.push(recordType);
        try {
            RecordComponent[] components = recordType.getRecordComponents();
            Class<?>[] parameterTypes = new Class<?>[components.length];
            Object[] arguments = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                parameterTypes[i] = components[i].getType();
                arguments[i] = dummyValue(components[i].getGenericType(), inProgress);
            }
            Constructor<?> constructor = recordType.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("더미 record 인스턴스 생성 실패: " + recordType, e);
        } finally {
            inProgress.pop();
        }
    }
}
