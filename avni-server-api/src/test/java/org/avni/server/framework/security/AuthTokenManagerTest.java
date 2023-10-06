package org.avni.server.framework.security;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import org.avni.server.config.AvniKeycloakConfig;
import org.avni.server.util.FileUtil;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.File;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.avni.server.framework.security.AuthTokenManager.AUTH_TOKEN;

public class AuthTokenManagerTest {

    public static final String HEADER_DUMMY_TOKEN = "dummyToken";
    public static final String QUERY_PARAM_DUMMY_TOKEN = "dummyToken2";
    public static final String QUERY_STRING_WITH_AUTH_TOKEN = AUTH_TOKEN+ QUERY_PARAM_DUMMY_TOKEN;
    public static final String EMPTY_QUERY_STRING = "";

    @Test
    public void getDerivedAuthTokenFromRequestHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthenticationFilter.AUTH_TOKEN_HEADER, HEADER_DUMMY_TOKEN);
        assertThat (AuthTokenManager.getInstance().getDerivedAuthToken(request, EMPTY_QUERY_STRING)).isEqualTo(HEADER_DUMMY_TOKEN) ;
    }

    @Test
    public void getDerivedAuthTokenFromQueryString() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat (AuthTokenManager.getInstance().getDerivedAuthToken(request, QUERY_STRING_WITH_AUTH_TOKEN)).isEqualTo(QUERY_PARAM_DUMMY_TOKEN) ;
    }

    @Test
    public void dontObtainDerivedAuthToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat (AuthTokenManager.getInstance().getDerivedAuthToken(request, EMPTY_QUERY_STRING)).isNull();
    }

    @Test
    public void obtainDerivedAuthToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthenticationFilter.AUTH_TOKEN_HEADER, HEADER_DUMMY_TOKEN);
        assertThat (AuthTokenManager.getInstance().getDerivedAuthToken(request, QUERY_STRING_WITH_AUTH_TOKEN)).isNotEmpty();
    }

    @Test
    public void obtainDerivedAuthTokenFromQueryStringOverridingHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthenticationFilter.AUTH_TOKEN_HEADER, HEADER_DUMMY_TOKEN);
        assertThat (AuthTokenManager.getInstance().getDerivedAuthToken(request, QUERY_STRING_WITH_AUTH_TOKEN)).isEqualTo(HEADER_DUMMY_TOKEN);
    }

    @Test
    public void decryptAuthToken() {
        AvniKeycloakConfig avniKeycloakConfig = new AvniKeycloakConfig();
        avniKeycloakConfig.postInit();
        PrivateKey privateKey = avniKeycloakConfig.getPrivateKey();
        String idToken = "eyJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIiwiY3R5IjoiSldUIiwia2lkIjoiYzA4ODE5YTYtYjIyYi00NWRiLWE3ODAtZjFmYjJhYzdhODM3In0.AdUy_VcTGsLTCaH-kbc5_omFXycSPts3iE6XpbjUWHT7iiXUifTHeS8cT2k45zK_t8N9ytfnwh01bCe92KNLlVWedKUjHiVmZoyogglnSVIwR36SDrUR2D3Vjbb-UM-ic-wx4irfuX5a6Q-5vAq1Xc3_eYbLbRtHJwxaIbfHnQ4sjiljmS0vtYaMbdm_GEjTNOpRbGN8XDxeykmo3IKj0HNEgIf17IfuC_jzfmEW58Kpt6Yi-fxNgAfc_wirI5P-rXkyUeuJjcn0cYH5yziiLnbISLapWvW_nOEUXHw6oplxiv8mH1aRuSbds2iWajIxT07FxKoLvfn4HtW7fBh0rFTtGdIu5CJiwlXFbcSX6jARYb9SSD45GCrP_z5uc22BTWLeWVgw.c8kYSYCVFNtZsTkd.E6GMB5_7knQ4ebDS4EmDD8O7xBe0Gv4VF0oIkpPP5kLD89ohI-AdChnyVdrMJgr0XNKcdQ4YfIoC9T13Cm1Mb-VNv7oqBoH1AHdkEBtgTDFReaiFbRB1ijKDbmsuzV6hz6-s2OyMXRPBlotfubCBI23i1FAIQiOWicgOeGZmleWz3_evab0HvqytU9QlBBsslR464pASHYFYMLxg5XEOmTuxc_NtnyX7ORchP5-zX296_m30CRHkuwq8zm3T2XkpWXYZTUpQDtFjac0ctz5bHwvqPlrji8m0Jns2jKLB0EUXcbH2hxv1fIl6fffJVLYhGlMjv8-4AgTgsCw77RvZd_kl6PSe7eAsQmUhSAp5Fb00Ck2SHRIzXlBDStLwiuKs-goXPIVPpiyX-AjVibFvryxARW5Ii0Vlc9y9C7AViWOD8luNw9Knx-_U3AWN07jV1HCJu-nGXUOzKku_ZegfX398LCKdah3Eln-4P3Myn2UFhKoc-ysXLI_s9g4Du6wJBtWCOANRTwcb3H8TKavM_XCh2JMTbhJyAKBk6M4cE86lPBbmPEGsg1phbPNR3BMZhEho6qK7235yZR4mPw-W7Za0kRCh6ngAAj5ZSKnVMsM-xNz-pOQK_6dCTLFEGDETH_oWO-anSgjhjcvfjecGDptS9TItbbgM4TOxsR2fXV7HCWy39KiyqK2LxJQnBA9UFaZ-ZBYapqX6p-wh5Kg63lpwtgJKalFDy2XCtWJ5f5O3ndeRyuld0U33BA32ukS4OQ2xLl27MI28yEJG4ufg-W0Gu2IR1fdseMF9VcKvPtMPghtjI5hofGLk07jjiNXwUh2GGDrX63Ysjud8sDVnG7ISDChPu9e93oJZUlxwH9RTpb3isxOnyCf46G_f1qxhl38Z3aHQ57W6_LhwPc8OeLKmU-4JKuf3gJFqXtZw-HYq8fqd1wECX_QI9apyVTeQsUBwIgitvfdXSqL4KXJDv-zTlmIBZJZZl2eBZ7xVzdvk-P1LZLUN6Ha4YdnUe7IkjXUuhy6x70Wmj8VpL1kLuiXCe6GYuLqAJEK13oCjO0vK-UUWs3jLfyTfJoSdVqqN-nQ3rja_KgY23bGu_DvynaVZ9OFpSlzPh5L7mVcALbfVsfOa_WUrnGUAFvBLdTRyPvWjJJYxQl12bXXeTCGuZreaJGbo4tKNATgYyol23iqKP9X9oSxAREvCFM38u6aXU2CUz7UTQB6bMJ_MqpAHab6rKFm3_kggBiU_JhLtkyx_Yd0aGkPm79L-u4DNYB2LR6LelFSYMOAF1-sVzwmdVZ0qLH2FOuIVRHNh_SOe35f2S_sl1M2H6FJGYlv4a_U4zpYqIy3tP-nyh7lZqRiXugnVU3Oyyb_PqSg8duhlDU2FNVJPw36ypoHc-fuWONH0PanWPo3UDCt1Pkzy-uc2eIqqF-Nz6bck35r_v0rN0ap9iXvhaAn5iCULjen3mXZZ9HfcMrLHDb61u5plvCdHKqwsDtzAILaMlEs_qrA3zg50Lh-Z_zPWMu011BqLXZfZLlBP0Ud_QOSX.eE0eXWkvL3LBDaP_3eF1rw";
        String decrypted = AuthTokenManager.getInstance().decryptAuthToken(idToken, privateKey);
        assertThat(decrypted).isNotEqualTo(idToken);
        assertThat(decrypted).isNotEqualTo(null);
    }
}
