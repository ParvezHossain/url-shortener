package com.parvez.urlshortener.safety;

import java.net.URI;

/** Pluggable reputation scanner; implementations must bound network work and never log URLs. */
public interface SafetyProvider {
    /** Returns a stable provider/configuration revision used to partition cached verdicts. */
    String name();
    /** Returns a definitive verdict or throws when no trustworthy verdict is available. */
    Verdict scan(URI destination);
    /** Represents only definitive provider responses; failures are never safe verdicts. */
    enum Verdict { SAFE, MALICIOUS }
}
