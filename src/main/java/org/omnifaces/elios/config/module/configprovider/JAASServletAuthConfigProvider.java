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

package org.omnifaces.elios.config.module.configprovider;

import java.util.Map;

import javax.security.auth.message.AuthException;
import javax.security.auth.message.config.AuthConfigFactory;
import javax.security.auth.message.module.ServerAuthModule;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.omnifaces.elios.config.delegate.MessagePolicyDelegate;
import org.omnifaces.elios.config.delegate.ServletMessagePolicyDelegate;
import org.omnifaces.elios.config.helper.ModulesManager;

/**
 *
 * @author Ron Monzillo
 */
public class JAASServletAuthConfigProvider extends JAASAuthConfigProvider {

    private static final String HTTP_SERVLET_LAYER = "HttpServlet";
    private static final String MANDATORY_KEY = "javax.security.auth.message.MessagePolicy.isMandatory";
    private static final String MANDATORY_AUTH_CONTEXT_ID = "mandatory";
    private static final String OPTIONAL_AUTH_CONTEXT_ID = "optional";
    private static final Class[] moduleTypes = new Class[] { ServerAuthModule.class };
    private static final Class[] messageTypes = new Class[] { HttpServletRequest.class, HttpServletResponse.class };
    final static MessagePolicyDelegate mandatoryPolicy = new ServletMessagePolicyDelegate();

    public JAASServletAuthConfigProvider(Map properties, AuthConfigFactory factory) {
        super(properties, factory);
    }

    @Override
    public MessagePolicyDelegate getMessagePolicyDelegate(String appContext) throws AuthException {
        return mandatoryPolicy;
    }

    @Override
    protected Class[] getModuleTypes() {
        return moduleTypes;
    }

    @Override
    protected String getLayer() {
        return HTTP_SERVLET_LAYER;
    }

    @Override
    public ModulesManager getAuthContextHelper(String appContext, boolean returnNullContexts) throws AuthException {
        // overrides returnNullContexts to false (as required by Servlet Profile)
        return super.getAuthContextHelper(appContext, false);
    }
}
