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

package org.omnifaces.elios.services.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;
import javax.security.auth.message.MessagePolicy;

import org.jvnet.hk2.config.types.Property;
import org.omnifaces.elios.config.factory.ConfigParser;
import org.omnifaces.elios.config.helper.AuthMessagePolicy;
import org.omnifaces.elios.config.module.configprovider.GFServerConfigProvider;
import org.omnifaces.enterprise.config.serverbeans.MessageSecurityConfig;
import org.omnifaces.enterprise.config.serverbeans.ProviderConfig;
import org.omnifaces.enterprise.config.serverbeans.RequestPolicy;
import org.omnifaces.enterprise.config.serverbeans.ResponsePolicy;
import org.omnifaces.enterprise.config.serverbeans.SecurityService;
import org.omnifaces.enterprise.security.common.Util;
import org.omnifaces.logging.LogDomains;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.internal.api.Globals;

import sun.security.util.PropertyExpander;
import sun.security.util.PropertyExpander.ExpandException;

/**
 * Parser for message-security-config in domain.xml
 */
public class ConfigDomainParser implements ConfigParser {

    private static Logger _logger = null;
    static {
        _logger = LogDomains.getLogger(ConfigDomainParser.class, LogDomains.SECURITY_LOGGER);
    }

    // configuration info
    private Map configMap = new HashMap();
    private Set<String> layersWithDefault = new HashSet<String>();

    public ConfigDomainParser() throws IOException {
    }

    public void initialize(Object service) throws IOException {
        if (service == null && Globals.getDefaultHabitat() != null) {
            service = Globals.getDefaultHabitat().getService(SecurityService.class, ServerEnvironment.DEFAULT_INSTANCE_NAME);
        }

        if (service instanceof SecurityService) {
            processServerConfig((SecurityService) service, configMap);
        } /*
           * else { throw new IOException("invalid configBean type passed to parser"); }
           */
    }

    private void processServerConfig(SecurityService service, Map newConfig) throws IOException {

        List<MessageSecurityConfig> configList = service.getMessageSecurityConfig();

        if (configList != null) {

            Iterator<MessageSecurityConfig> cit = configList.iterator();

            while (cit.hasNext()) {

                MessageSecurityConfig next = cit.next();

                // single message-security-config for each auth-layer
                // auth-layer is synonymous with intercept

                String intercept = parseInterceptEntry(next, newConfig);

                List<ProviderConfig> provList = next.getProviderConfig();

                if (provList != null) {

                    Iterator<ProviderConfig> pit = provList.iterator();

                    while (pit.hasNext()) {

                        ProviderConfig provider = pit.next();
                        parseIDEntry(provider, newConfig, intercept);
                    }
                }
            }
        }
    }

    public Map getConfigMap() {
        return configMap;
    }

    public Set<String> getLayersWithDefault() {
        return layersWithDefault;
    }

    private String parseInterceptEntry(MessageSecurityConfig msgConfig, Map newConfig) throws IOException {

        String intercept = null;
        String defaultServerID = null;
        String defaultClientID = null;

        intercept = msgConfig.getAuthLayer();
        defaultServerID = msgConfig.getDefaultProvider();
        defaultClientID = msgConfig.getDefaultClientProvider();

        if (_logger.isLoggable(Level.FINE)) {
            _logger.fine("Intercept Entry: " + "\n    intercept: " + intercept + "\n    defaultServerID: " + defaultServerID + "\n    defaultClientID:  "
                    + defaultClientID);
        }

        if (defaultServerID != null || defaultClientID != null) {
            layersWithDefault.add(intercept);
        }

        GFServerConfigProvider.InterceptEntry intEntry = (GFServerConfigProvider.InterceptEntry) newConfig.get(intercept);

        if (intEntry != null) {
            throw new IOException("found multiple MessageSecurityConfig " + "entries with the same auth-layer");
        }

        // create new intercept entry
        intEntry = new GFServerConfigProvider.InterceptEntry(defaultClientID, defaultServerID, null);
        newConfig.put(intercept, intEntry);
        return intercept;
    }

    private void parseIDEntry(ProviderConfig pConfig, Map newConfig, String intercept) throws IOException {

        String id = pConfig.getProviderId();
        String type = pConfig.getProviderType();
        String moduleClass = pConfig.getClassName();
        MessagePolicy requestPolicy = parsePolicy((RequestPolicy) pConfig.getRequestPolicy());
        MessagePolicy responsePolicy = parsePolicy((ResponsePolicy) pConfig.getResponsePolicy());

        // get the module options

        Map options = new HashMap();
        String key;
        String value;

        List<Property> pList = pConfig.getProperty();

        if (pList != null) {

            Iterator<Property> pit = pList.iterator();

            while (pit.hasNext()) {

                Property property = pit.next();

                try {
                    options.put(property.getName(), PropertyExpander.expand(property.getValue(), false));
                } catch (ExpandException ee) {
                    // log warning and give the provider a chance to
                    // interpret value itself.
                    if (_logger.isLoggable(Level.FINE)) {
                        _logger.log(Level.FINE, "jmac.unexpandedproperty");
                    }
                    options.put(property.getName(), property.getValue());
                }
            }
        }

        if (_logger.isLoggable(Level.FINE)) {
            _logger.fine("ID Entry: " + "\n    module class: " + moduleClass + "\n    id: " + id + "\n    type: " + type + "\n    request policy: "
                    + requestPolicy + "\n    response policy: " + responsePolicy + "\n    options: " + options);
        }

        // create ID entry
        GFServerConfigProvider.IDEntry idEntry = new GFServerConfigProvider.IDEntry(type, moduleClass, requestPolicy, responsePolicy, options);

        GFServerConfigProvider.InterceptEntry intEntry = (GFServerConfigProvider.InterceptEntry) newConfig.get(intercept);
        if (intEntry == null) {
            throw new IOException("intercept entry for " + intercept + " must be specified before ID entries");
        }

        if (intEntry.idMap == null) {
            intEntry.idMap = new HashMap();
        }

        // map id to Intercept
        intEntry.idMap.put(id, idEntry);
    }

    private MessagePolicy parsePolicy(RequestPolicy policy) {

        if (policy == null) {
            return null;
        }

        String authSource = policy.getAuthSource();
        String authRecipient = policy.getAuthRecipient();
        return AuthMessagePolicy.getMessagePolicy(authSource, authRecipient);
    }

    private MessagePolicy parsePolicy(ResponsePolicy policy) {

        if (policy == null) {
            return null;
        }

        String authSource = policy.getAuthSource();
        String authRecipient = policy.getAuthRecipient();
        return AuthMessagePolicy.getMessagePolicy(authSource, authRecipient);
    }
}
