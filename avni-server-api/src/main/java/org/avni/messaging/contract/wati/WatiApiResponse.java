package org.avni.messaging.contract.wati;

/**
 * Response body from Wati API calls.
 * result=true means the message was accepted by Wati for delivery.
 */
public class WatiApiResponse {

    private boolean result;
    private String info;

    public boolean isResult() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }
}
