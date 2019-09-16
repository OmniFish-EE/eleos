package org.omnifaces.elios.config.data;

import java.util.Map;

import javax.security.auth.message.MessagePolicy;

/**
 * parsed ID entry
 */
public class AuthModuleConfig {
    public String type; // provider type (client, server, client-server)
    public String moduleClassName;
    public MessagePolicy requestPolicy;
    public MessagePolicy responsePolicy;
    public Map options;

    public String getModuleClassName() {
        return moduleClassName;
    }

    public Map getOptions() {
        return options;
    }

    public MessagePolicy getRequestPolicy() {
        return requestPolicy;
    }

    public MessagePolicy getResponsePolicy() {
        return responsePolicy;
    }

    public String getType() {
        return type;
    }

    public AuthModuleConfig(String type, String moduleClassName, MessagePolicy requestPolicy, MessagePolicy responsePolicy, Map options) {
        this.type = type;
        this.moduleClassName = moduleClassName;
        this.requestPolicy = requestPolicy;
        this.responsePolicy = responsePolicy;
        this.options = options;
    }
}