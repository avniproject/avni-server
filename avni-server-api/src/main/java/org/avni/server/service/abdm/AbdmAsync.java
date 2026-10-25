package org.avni.server.service.abdm;

import org.springframework.stereotype.Component;

import java.time.Duration;

// DEMO-ONLY abdm-hackathon: never merged.
// Runs callback work off the request thread, as the organisation (AbdmOrgContext). Never throws to the caller.
@Component
public class AbdmAsync {
    public void submit(String what, Runnable work) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }

    public void submitAfter(Duration delay, String what, Runnable work) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }
}
