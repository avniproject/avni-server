package org.avni.server.service.abha;

import org.avni.server.domain.AbhaEvent;

// Eka's word, before a record is stored, on whether its Eka ID is the patient Eka holds under its ABHA. Unverified
// whether a HIP's token may read a profile by Eka ID, so until the sandbox shows it, NotAskedEkaAbhaPairing asks nothing.
public interface EkaAbhaPairing {
    enum Verdict {CONFIRMED, REFUTED, NOT_ASKED}

    Verdict check(AbhaEvent record);
}
