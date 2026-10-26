package org.avni.server.service.abdm.hip;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

// DEMO-ONLY abdm-hackathon: linking on save (HIP_INTI_LINK_505), on or off. Starts from abdm.auto-link and is switched from
// the admin screen while the server runs, so a test of linking from the patient's app has visits nothing has linked yet.
@Component
public class AbdmAutoLink {
    private final AtomicBoolean on;

    @Autowired
    public AbdmAutoLink(@Value("${abdm.auto-link:true}") boolean on) {
        this.on = new AtomicBoolean(on);
    }

    public boolean isOn() {
        return on.get();
    }

    public void set(boolean value) {
        on.set(value);
    }
}
