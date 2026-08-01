package com.hot6ix.upbid.domain.auth.sms.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryVerificationCodeStoreTest {

    private InMemoryVerificationCodeStore store;

    private static final String PHONE_NUMBER = "01012345678";

    @BeforeEach
    void setUp() {
        store = new InMemoryVerificationCodeStore();
    }

    private VerificationEntry newEntry(String code) {
        return new VerificationEntry(
                code,
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(4),
                0
        );
    }

    // ==================== save / find / delete ====================

    @Test
    @DisplayName("저장한 엔트리를 조회할 수 있다")
    void save_그리고_find() {
        VerificationEntry entry = newEntry("123456");
        store.save(PHONE_NUMBER, entry);

        assertThat(store.find(PHONE_NUMBER)).contains(entry);
    }

    @Test
    @DisplayName("새 인증번호를 저장하면 기존 엔트리가 덮어써진다")
    void save_기존_엔트리_덮어쓰기() {
        store.save(PHONE_NUMBER, newEntry("111111"));
        VerificationEntry newEntry = newEntry("999999");
        store.save(PHONE_NUMBER, newEntry);

        assertThat(store.find(PHONE_NUMBER)).contains(newEntry);
        assertThat(store.find(PHONE_NUMBER).get().code()).isEqualTo("999999");
    }

    @Test
    @DisplayName("없는 번호를 조회하면 빈 Optional을 반환한다")
    void find_없는_번호() {
        assertThat(store.find(PHONE_NUMBER)).isEmpty();
    }

    @Test
    @DisplayName("삭제 후 조회하면 빈 Optional을 반환한다")
    void delete_후_find() {
        store.save(PHONE_NUMBER, newEntry("123456"));
        store.delete(PHONE_NUMBER);

        assertThat(store.find(PHONE_NUMBER)).isEmpty();
    }

    // ==================== incrementFailCount ====================

    @Test
    @DisplayName("실패 횟수를 증가시키면 저장된 엔트리의 failCount가 1 늘어난다")
    void incrementFailCount_실패_횟수_증가() {
        store.save(PHONE_NUMBER, newEntry("123456"));
        store.incrementFailCount(PHONE_NUMBER);

        assertThat(store.find(PHONE_NUMBER).get().failCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 번호의 실패 횟수를 증가시켜도 예외가 발생하지 않는다")
    void incrementFailCount_없는_번호() {
        store.incrementFailCount(PHONE_NUMBER); // 예외 없이 무시
        assertThat(store.find(PHONE_NUMBER)).isEmpty();
    }

    // ==================== recordSend / countSendsWithinHour / countSendsWithinDay ====================

    @Test
    @DisplayName("발송을 기록하면 1시간 내 발송 횟수에 반영된다")
    void recordSend_시간당_횟수_반영() {
        store.recordSend(PHONE_NUMBER);
        store.recordSend(PHONE_NUMBER);

        assertThat(store.countSendsWithinHour(PHONE_NUMBER)).isEqualTo(2);
    }

    @Test
    @DisplayName("발송을 기록하면 하루 발송 횟수에 반영된다")
    void recordSend_일일_횟수_반영() {
        store.recordSend(PHONE_NUMBER);
        store.recordSend(PHONE_NUMBER);
        store.recordSend(PHONE_NUMBER);

        assertThat(store.countSendsWithinDay(PHONE_NUMBER)).isEqualTo(3);
    }

    @Test
    @DisplayName("발송 이력이 없는 번호의 횟수는 0이다")
    void count_발송_이력_없음() {
        assertThat(store.countSendsWithinHour(PHONE_NUMBER)).isZero();
        assertThat(store.countSendsWithinDay(PHONE_NUMBER)).isZero();
    }

    @Test
    @DisplayName("1시간이 지난 발송 이력은 시간당 횟수에서 제외된다")
    @SuppressWarnings("unchecked")
    void countSendsWithinHour_1시간_이전_항목_제외() {
        Map<String, CopyOnWriteArrayList<LocalDateTime>> sendHistory =
                (Map<String, CopyOnWriteArrayList<LocalDateTime>>) ReflectionTestUtils.getField(store, "sendHistory");

        CopyOnWriteArrayList<LocalDateTime> times = new CopyOnWriteArrayList<>();
        times.add(LocalDateTime.now().minusHours(2)); // 2시간 전 → 제외 대상
        times.add(LocalDateTime.now());               // 현재 → 포함 대상
        sendHistory.put(PHONE_NUMBER, times);

        assertThat(store.countSendsWithinHour(PHONE_NUMBER)).isEqualTo(1);
    }

    @Test
    @DisplayName("24시간이 지난 발송 이력은 일일 횟수에서 제외된다")
    @SuppressWarnings("unchecked")
    void countSendsWithinDay_24시간_이전_항목_제외() {
        Map<String, CopyOnWriteArrayList<LocalDateTime>> sendHistory =
                (Map<String, CopyOnWriteArrayList<LocalDateTime>>) ReflectionTestUtils.getField(store, "sendHistory");

        CopyOnWriteArrayList<LocalDateTime> times = new CopyOnWriteArrayList<>();
        times.add(LocalDateTime.now().minusDays(2)); // 2일 전 → 제외 대상
        times.add(LocalDateTime.now());              // 현재 → 포함 대상
        sendHistory.put(PHONE_NUMBER, times);

        assertThat(store.countSendsWithinDay(PHONE_NUMBER)).isEqualTo(1);
    }
}
