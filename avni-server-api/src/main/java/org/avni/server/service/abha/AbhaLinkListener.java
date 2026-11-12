package org.avni.server.service.abha;

import org.avni.server.domain.Individual;

// Told of each subject save in an organisation with ABHA on, inside the save, once the subject's ABHA link is kept;
// changed says whether this save made, changed or voided that link.
public interface AbhaLinkListener {
    void abhaLinkKept(Individual subject, boolean changed);
}
