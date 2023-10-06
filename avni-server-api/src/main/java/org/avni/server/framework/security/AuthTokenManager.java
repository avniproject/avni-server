package org.avni.server.framework.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jwt.EncryptedJWT;
import org.avni.server.web.CommentThreadController;
import org.avni.server.web.LogoutController;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.regex.Pattern;

public class AuthTokenManager {
    public static final String AUTH_TOKEN_COOKIE = "auth-token";
    public static final Pattern PARAM_SEPARATOR_PATTERN = Pattern.compile("[&;]");
    public static final String AUTH_TOKEN = "AUTH-TOKEN=";
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CommentThreadController.class);

    public static AuthTokenManager getInstance() {
        return new AuthTokenManager();
    }

    String getDerivedAuthToken(HttpServletRequest request, String queryString) {
        return this.getDerivedAuthToken(request, queryString, null);
    }

    public String getDerivedAuthToken(HttpServletRequest request, String queryString, PrivateKey privateKey) {
        String idToken = request.getHeader(AuthenticationFilter.AUTH_TOKEN_HEADER);
        idToken = getAuthTokenFromQueryString(idToken, queryString);
        String derivedIdToken = idToken;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            Cookie authCookie = Arrays.stream(request.getCookies()).filter(cookie -> cookie.getName().equals(AUTH_TOKEN_COOKIE)).findAny().orElse(null);
            if ((idToken == null || idToken.isEmpty()) && authCookie != null && !authCookie.getValue().isEmpty()) {
                derivedIdToken = authCookie.getValue();
            }
        }
        return decryptAuthToken(derivedIdToken, privateKey);
    }

    String decryptAuthToken(String idToken, PrivateKey privateKey)  {
        if (privateKey == null) return idToken;

        try {
            EncryptedJWT jwt = EncryptedJWT.parse(idToken);
            RSADecrypter decrypter = new RSADecrypter(privateKey);
            jwt.decrypt(decrypter);
            return jwt.getPayload().toString();
//            JWEObject jweObject = JWEObject.parse(Base64.getDecoder().decode(idToken));
//            jweObject.decrypt(new RSADecrypter(privateKey));
//            Payload payload = jweObject.getPayload();
//            return payload.toString();

//            Cipher cipher = Cipher.getInstance("RSA-OAEP");
//            cipher.init(Cipher.DECRYPT_MODE, privateKey);
//
//            byte[] bytes = cipher.doFinal(idToken.getBytes());
//            return new String(bytes);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage(), e);
//            throw new RuntimeException(e);
            return null;
        }
    }

    public void setAuthCookie(HttpServletRequest request, HttpServletResponse response, String authToken) {
        if (request.getRequestURI().equals(LogoutController.LOGOUT_URL)) {
            response.addCookie(makeCookie("", 0));
            return;
        }
        if (authToken != null && !authToken.isEmpty()) {
            response.addCookie(makeCookie(authToken, getCookieMaxAge(authToken)));
        }
    }

    private int getCookieMaxAge(String authToken) {
        DecodedJWT jwt = JWT.decode(authToken);
        int expiryDuration = (int) ((jwt.getExpiresAt().getTime() - new Date().getTime()) / 1000) - 60;
        return Math.max(expiryDuration, 0);
    }

    private Cookie makeCookie(String value, int age) {
        Cookie cookie = new Cookie(AUTH_TOKEN_COOKIE, value);
        cookie.setMaxAge(age);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        return cookie;
    }

    /**
     * @param authToken
     * @param queryString
     * @return param authToken, if it has content.
     * queryAuthToken, if param authToken is empty and param queryString contains an auth-token.
     * null, in all other cases.
     */
    private String getAuthTokenFromQueryString(String authToken, String queryString) {
        if (!StringUtils.hasText(authToken) && StringUtils.hasText(queryString)) {
            return parseAuthToken(queryString);
        }
        return authToken;
    }

    private String parseAuthToken(String query) {
        if (query != null) {
            String[] params = PARAM_SEPARATOR_PATTERN.split(query);
            for (String param : params) {
                if (param.startsWith(AUTH_TOKEN)) {
                    return param.substring(AUTH_TOKEN.length());
                }
            }
        }
        return null;
    }
}
