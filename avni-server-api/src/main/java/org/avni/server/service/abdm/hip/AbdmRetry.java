package org.avni.server.service.abdm.hip;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

// DEMO-ONLY abdm-hackathon: never merged.
// An Eka webhook can arrive before the thread that sent the request has finished; the correlation row is written before
// the send, but the lookup still gets a few tries. DEMO-ONLY: 10 x 500 ms, then the caller logs and gives up.
@Component
public class AbdmRetry {
    private final int attempts;
    private final long sleepMs;

    @Autowired
    public AbdmRetry() {
        this(10, 500);
    }

    public AbdmRetry(int attempts, long sleepMs) {
        this.attempts = attempts;
        this.sleepMs = sleepMs;
    }

    public <T> Optional<T> find(Supplier<Optional<T>> lookup) {
        for (int i = 0; i < attempts; i++) {
            Optional<T> found = lookup.get();
            if (found.isPresent()) return found;
            if (i < attempts - 1 && sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }
}
