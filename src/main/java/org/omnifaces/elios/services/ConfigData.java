package org.omnifaces.elios.services;

import javax.security.auth.message.config.AuthConfig;
import javax.security.auth.message.config.AuthConfigProvider;
import javax.security.auth.message.config.ClientAuthConfig;
import javax.security.auth.message.config.ServerAuthConfig;

public class ConfigData {

    AuthConfigProvider provider;
    AuthConfig sConfig;
    AuthConfig cConfig;

    ConfigData() {
        provider = null;
        sConfig = null;
        cConfig = null;
    }

    ConfigData(AuthConfigProvider p, AuthConfig a) {
        provider = p;
        if (a == null) {
            sConfig = null;
            cConfig = null;
        } else if (a instanceof ServerAuthConfig) {
            sConfig = a;
            cConfig = null;
        } else if (a instanceof ClientAuthConfig) {
            sConfig = null;
            cConfig = a;
        } else {
            throw new IllegalArgumentException();
        }
    }
}