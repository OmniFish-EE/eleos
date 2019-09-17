package org.omnifaces.elios.config.module.config;

import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.config.AuthConfigProvider;
import javax.security.auth.message.config.ClientAuthConfig;
import javax.security.auth.message.config.ClientAuthContext;
import javax.security.auth.message.module.ClientAuthModule;

import org.omnifaces.elios.config.module.context.GFClientAuthContext;
import org.omnifaces.elios.data.AuthModuleInstanceHolder;

public class GFClientAuthConfig extends GFAuthConfig implements ClientAuthConfig {

    public GFClientAuthConfig(AuthConfigProvider provider, String layer, String appContext, CallbackHandler handler) {
        super(provider, layer, appContext, handler, CLIENT);
    }

    public ClientAuthContext getAuthContext(String authContextID, Subject clientSubject, Map properties) throws AuthException {
        ClientAuthContext clientAuthContext = null;
        AuthModuleInstanceHolder authModuleInstanceHolder = getModuleInfo(authContextID, properties);

        if (authModuleInstanceHolder != null && authModuleInstanceHolder.getModule() != null) {
            Object moduleObj = authModuleInstanceHolder.getModule();
            Map map = authModuleInstanceHolder.getMap();
            if (moduleObj instanceof ClientAuthModule) {
                clientAuthContext = new GFClientAuthContext(this, (ClientAuthModule) moduleObj, map);
            }
        }

        return clientAuthContext;
    }
}