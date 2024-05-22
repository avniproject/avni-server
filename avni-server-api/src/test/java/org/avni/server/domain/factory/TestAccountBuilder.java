package org.avni.server.domain.factory;

import org.avni.server.domain.Account;

public class TestAccountBuilder {
    private final Account account;

    public TestAccountBuilder() {
        account = new Account();
    }

    public TestAccountBuilder withName(String name) {
        account.setName(name);
        return this;
    }

    public TestAccountBuilder withRegion(String region) {
        account.setRegion(region);
        return this;
    }

    public Account build() {
        return account;
    }
}
