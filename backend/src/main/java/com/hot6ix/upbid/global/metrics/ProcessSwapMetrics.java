package com.hot6ix.upbid.global.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 이 프로세스가 디스크로 내려간 양(스왑)과 실제 메모리에 있는 양을 내보낸다.
 *
 * <p><b>GC 정지 시간을 설명하려면 이 값이 필요하다.</b> Full GC 는 오래 사는 영역 전체를
 * 처음부터 끝까지 훑는데, 그중 스왑된 페이지를 만나면 디스크에서 끌어와야 한다. 메모리 접근이
 * 나노초이고 디스크는 밀리초라 정지 시간이 자릿수로 달라진다.
 *
 * <p>실측(t4g.micro, 906MB)에서 JVM 이 메모리 384MB 에 스왑 429MB 를 쓰고 있었고, 같은 서버의
 * Full GC 정지가 기동 직후 229ms 에서 40분 뒤 3,855ms 로 늘었다. 힙이 차서인지 스왑에서
 * 끌어와서인지를 가르려면 두 값을 <b>같은 측정 구간에</b> 나란히 놓아야 한다.
 *
 * <p>Micrometer 의 JVM 지표는 힙 안쪽만 본다. 힙이 디스크에 있는지는 OS 만 알기 때문에 여기서
 * {@code /proc/self/status} 를 읽는다.
 *
 * <p><b>리눅스가 아니면 지표를 만들지 않는다.</b> {@code /proc} 이 없는 곳(개발자 맥북의 테스트
 * 실행 등)에서 0 을 내보내면 "스왑을 안 쓴다"로 읽혀서, 못 잰 것과 0 이 구분되지 않는다.
 */
@Slf4j
@Component
public class ProcessSwapMetrics implements MeterBinder {

    private static final Path STATUS = Path.of("/proc/self/status");

    @Override
    public void bindTo(MeterRegistry registry) {

        if (!Files.isReadable(STATUS)) {
            log.debug("/proc/self/status 를 못 읽어 스왑 지표를 만들지 않는다 (리눅스가 아닌 환경)");
            return;
        }

        Gauge.builder("process.swap.bytes", () -> readKb("VmSwap") * 1024.0)
                .description("이 프로세스가 스왑으로 내려간 양")
                .baseUnit("bytes")
                .register(registry);

        Gauge.builder("process.rss.bytes", () -> readKb("VmRSS") * 1024.0)
                .description("이 프로세스가 실제 메모리에 올려 둔 양")
                .baseUnit("bytes")
                .register(registry);
    }

    /**
     * {@code /proc/self/status} 에서 한 줄을 읽는다. 못 읽으면 {@code NaN} 이다.
     *
     * <p>0 이 아니라 {@code NaN} 을 돌려주는 이유는 위 클래스 설명과 같다. 스크레이프마다
     * 파일을 다시 읽는데, 이 파일은 커널이 그때 만들어 주는 것이라 캐시하면 값이 안 바뀐다.
     */
    private double readKb(String key) {
        try {
            List<String> lines = Files.readAllLines(STATUS);
            for (String line : lines) {
                if (line.startsWith(key + ":")) {
                    return Double.parseDouble(line.replaceAll("[^0-9]", ""));
                }
            }
        } catch (IOException | NumberFormatException e) {
            log.debug("{} 를 못 읽었다: {}", key, e.toString());
        }
        return Double.NaN;
    }
}
