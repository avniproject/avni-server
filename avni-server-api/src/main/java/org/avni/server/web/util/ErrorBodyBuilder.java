package org.avni.server.web.util;

import org.avni.server.util.ExceptionUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ErrorBodyBuilder {
    @Value("${avni.exception.in.response}")
    private boolean sendExceptionInResponse;

    public static ErrorBodyBuilder createForTest() {
        ErrorBodyBuilder errorBodyBuilder = new ErrorBodyBuilder();
        errorBodyBuilder.sendExceptionInResponse = true;
        return errorBodyBuilder;
    }

    public ErrorBodyBuilder() {
    }

    public String getErrorBody(Exception e) {
        return sendExceptionInResponse ? ExceptionUtil.getFullStackTrace(e) : "";
    }

    public String getErrorBody(String s) {
        return sendExceptionInResponse ? s : "";
    }

    public String getErrorMessageBody(Throwable t) {
        return sendExceptionInResponse ? t.getMessage() : "";
    }

    public Object getErrorBody(Object o) {
        return sendExceptionInResponse ? o : "";
    }
}
