package org.avni.server.service;

import com.auth0.jwk.SigningKeyNotFoundException;
import org.avni.server.domain.User;

public interface IAMAuthService {
    User getUserFromToken(String token) throws SigningKeyNotFoundException;
}
