package com.barbie.dreamworld_api.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Buckets de rate limiting por IP + tipo de operação.
 *
 * Os limites vêm de variáveis de ambiente para poderem ser ajustados no Render
 * sem recompilar. Buckets antigos são removidos para evitar crescimento
 * ilimitado do mapa (DoS por IPs aleatórios).
 */
@Configuration
public class RateLimitConfig {

    private static final int MAX_BUCKETS = 2000;
    private static final long LIMPEZA_INTERVALO_MS = 60_000;
    private static final long BUCKET_IDLE_MS = 10 * 60_000;

    private final Map<String, BucketEntry> buckets = new ConcurrentHashMap<>();
    private volatile long ultimaLimpeza = System.currentTimeMillis();

    @Value("${app.rate-limit.read-capacity:10}")
    private int readCapacity;

    @Value("${app.rate-limit.write-capacity:5}")
    private int writeCapacity;

    @Value("${app.rate-limit.window-seconds:60}")
    private long windowSeconds;

    public Bucket resolveBucket(String ip, boolean isWrite) {
        limparSeNecessario();

        String key = ip + ":" + (isWrite ? "W" : "R");
        return buckets.computeIfAbsent(key, k -> new BucketEntry(buildBucket(isWrite)))
                .touch()
                .bucket;
    }

    private Bucket buildBucket(boolean write) {
        int capacity = write ? writeCapacity : readCapacity;
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, Duration.ofSeconds(windowSeconds))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private void limparSeNecessario() {
        long agora = System.currentTimeMillis();
        if (buckets.size() < MAX_BUCKETS || agora - ultimaLimpeza < LIMPEZA_INTERVALO_MS) {
            return;
        }
        synchronized (this) {
            if (agora - ultimaLimpeza < LIMPEZA_INTERVALO_MS) {
                return;
            }
            buckets.entrySet().removeIf(e -> agora - e.getValue().ultimoAcesso > BUCKET_IDLE_MS);
            ultimaLimpeza = agora;
        }
    }

    private static final class BucketEntry {
        private final Bucket bucket;
        private volatile long ultimoAcesso;

        private BucketEntry(Bucket bucket) {
            this.bucket = bucket;
            this.ultimoAcesso = System.currentTimeMillis();
        }

        private BucketEntry touch() {
            this.ultimoAcesso = System.currentTimeMillis();
            return this;
        }
    }
}
