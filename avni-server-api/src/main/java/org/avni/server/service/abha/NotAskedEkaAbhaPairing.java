package org.avni.server.service.abha;

import org.avni.server.domain.AbhaEvent;
import org.springframework.stereotype.Component;

// Eka is not asked whether an Eka ID is the ABHA's until the sandbox shows a HIP may ask (EkaAbhaPairing).
@Component
public class NotAskedEkaAbhaPairing implements EkaAbhaPairing {
    @Override
    public Verdict check(AbhaEvent record) {
        return Verdict.NOT_ASKED;
    }
}
