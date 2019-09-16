/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.omnifaces.elios.services;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.config.AuthConfig;
import javax.security.auth.message.config.AuthConfigFactory;
import javax.security.auth.message.config.AuthConfigFactory.RegistrationContext;
import javax.security.auth.message.config.AuthConfigProvider;
import javax.security.auth.message.config.ClientAuthConfig;
import javax.security.auth.message.config.ClientAuthContext;
import javax.security.auth.message.config.RegistrationListener;
import javax.security.auth.message.config.ServerAuthConfig;
import javax.security.auth.message.config.ServerAuthContext;

import org.omnifaces.elios.config.helper.AuthMessagePolicy;
import org.omnifaces.elios.services.config.CallbackHandlerConfig;
import org.omnifaces.elios.services.config.HandlerContext;

/**
 * This is based Helper class for 196 Configuration. This class implements RegistrationListener.
 */
public abstract class BaseAuthenticationService {
    private static final String DEFAULT_HANDLER_CLASS = "com.sun.enterprise.security.jmac.callback.ContainerCallbackHandler";

//    private static String handlerClassName = null;
    protected static final AuthConfigFactory factory = AuthConfigFactory.getFactory();

    private ReadWriteLock rwLock;
    private Lock rLock;
    private Lock wLock;

    protected String layer;
    protected String appCtxt;
    protected Map map;
    protected CallbackHandler cbh;
    protected AuthConfigRegistrationWrapper listenerWrapper = null;

    protected void init(String layer, String appContext, Map map, CallbackHandler cbh) {

        this.layer = layer;
        this.appCtxt = appContext;
        this.map = map;
        this.cbh = cbh;
        if (this.cbh == null) {
            this.cbh = getCallbackHandler();
        }

        this.rwLock = new ReentrantReadWriteLock(true);
        this.rLock = rwLock.readLock();
        this.wLock = rwLock.writeLock();

        listenerWrapper = new AuthConfigRegistrationWrapper(this.layer, this.appCtxt);

    }

    public void setJmacProviderRegisID(String jmacProviderRegisID) {
        this.listenerWrapper.setJmacProviderRegisID(jmacProviderRegisID);
    }

    public AuthConfigRegistrationWrapper getRegistrationWrapper() {
        return this.listenerWrapper;
    }

    public void setRegistrationWrapper(AuthConfigRegistrationWrapper wrapper) {
        this.listenerWrapper = wrapper;
    }

    public AuthConfigRegistrationWrapper.AuthConfigRegistrationListener getRegistrationListener() {
        return this.listenerWrapper.getListener();
    }

    public void disable() {
        listenerWrapper.disable();
    }

    public Object getProperty(String key) {
        return map == null ? null : map.get(key);
    }

    public String getAppContextID() {
        return appCtxt;
    }

    public ClientAuthConfig getClientAuthConfig() throws AuthException {
        return (ClientAuthConfig) getAuthConfig(false);
    }

    public ServerAuthConfig getServerAuthConfig() throws AuthException {
        return (ServerAuthConfig) getAuthConfig(true);
    }

    public ClientAuthContext getClientAuthContext(MessageInfo info, Subject s) throws AuthException {
        ClientAuthConfig c = (ClientAuthConfig) getAuthConfig(false);
        if (c != null) {
            return c.getAuthContext(c.getAuthContextID(info), s, map);
        }
        return null;
    }

    public ServerAuthContext getServerAuthContext(MessageInfo info, Subject s) throws AuthException {
        ServerAuthConfig c = (ServerAuthConfig) getAuthConfig(true);
        if (c != null) {
            return c.getAuthContext(c.getAuthContextID(info), s, map);
        }
        return null;
    }

    protected AuthConfig getAuthConfig(AuthConfigProvider p, boolean isServer) throws AuthException {
        AuthConfig c = null;
        if (p != null) {
            if (isServer) {
                c = p.getServerAuthConfig(layer, appCtxt, cbh);
            } else {
                c = p.getClientAuthConfig(layer, appCtxt, cbh);
            }
        }
        return c;
    }

    protected AuthConfig getAuthConfig(boolean isServer) throws AuthException {

        ConfigData d = null;
        AuthConfig c = null;
        boolean disabled = false;
        AuthConfigProvider lastP = null;

        try {
            rLock.lock();
            disabled = (!listenerWrapper.isEnabled());
            if (!disabled) {
                d = listenerWrapper.getConfigData();
                if (d != null) {
                    c = (isServer ? d.sConfig : d.cConfig);
                    lastP = d.provider;
                }
            }

        } finally {
            rLock.unlock();
            if (disabled || c != null || (d != null && lastP == null)) {
                return c;
            }
        }

        // d == null || (d != null && lastP != null && c == null)
        if (d == null) {
            try {
                wLock.lock();
                if (listenerWrapper.getConfigData() == null) {
                    AuthConfigProvider nextP = factory.getConfigProvider(layer, appCtxt, this.getRegistrationListener());
                    if (nextP != null) {
                        listenerWrapper.setConfigData(new ConfigData(nextP, getAuthConfig(nextP, isServer)));
                    } else {
                        listenerWrapper.setConfigData(new ConfigData());
                    }
                }
                d = listenerWrapper.getConfigData();
            } finally {
                wLock.unlock();
            }
        }

        return ((isServer) ? d.sConfig : d.cConfig);
    }

    /**
     * Check if there is a provider register for a given layer and appCtxt.
     */
    protected boolean hasExactMatchAuthProvider() {
        boolean exactMatch = false;
        // XXX this may need to be optimized
        AuthConfigProvider p = factory.getConfigProvider(layer, appCtxt, null);
        if (p != null) {
            String[] IDs = factory.getRegistrationIDs(p);
            for (String i : IDs) {
                RegistrationContext c = factory.getRegistrationContext(i);
                if (layer.equals(c.getMessageLayer()) && appCtxt.equals(c.getAppContext())) {
                    exactMatch = true;
                    break;
                }
            }
        }

        return exactMatch;
    }

    /**
     * Get the callback default handler
     */
    private CallbackHandler getCallbackHandler() {

        CallbackHandler rvalue = AuthMessagePolicy.getDefaultCallbackHandler();
        if (rvalue instanceof CallbackHandlerConfig) {
            ((CallbackHandlerConfig) rvalue).setHandlerContext(getHandlerContext(map));
        }

        return rvalue;
    }

    /**
     * This method is invoked by the constructor and should be overrided by subclass.
     */
    protected HandlerContext getHandlerContext(Map map) {
        return null;
    }

    
    
}
