package org.omnifaces.elios.config.module.config;

import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.config.AuthConfigProvider;
import javax.security.auth.message.config.ServerAuthConfig;
import javax.security.auth.message.config.ServerAuthContext;
import javax.security.auth.message.module.ServerAuthModule;

import org.omnifaces.elios.config.data.AuthModuleInstanceHolder;
import org.omnifaces.elios.config.module.context.GFServerAuthContext;

public class GFServerAuthConfig extends GFAuthConfig implements ServerAuthConfig {

    public GFServerAuthConfig(AuthConfigProvider provider, String layer, String appContext, CallbackHandler handler) {
        super(provider, layer, appContext, handler, SERVER);
    }

    public ServerAuthContext getAuthContext(String authContextID, Subject serviceSubject, Map properties) throws AuthException {
        ServerAuthContext serverAuthContext = null;
        AuthModuleInstanceHolder authModuleInstanceHolder = getModuleInfo(authContextID, properties);

        if (authModuleInstanceHolder != null && authModuleInstanceHolder.getModule() != null) {
            Object moduleObj = authModuleInstanceHolder.getModule();
            Map map = authModuleInstanceHolder.getMap();
            if (moduleObj instanceof ServerAuthModule) {
                serverAuthContext = new GFServerAuthContext(this, (ServerAuthModule) moduleObj, map);
            }
        }

        return serverAuthContext;
    }
}