/*
 * Copyright (c) 2022, 2022 OmniFish and/or its affiliates. All rights reserved.
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

package org.omnifaces.eleos.config.factory;

import static java.util.logging.Level.WARNING;
import static org.omnifaces.eleos.config.helper.LogManager.JASPIC_LOGGER;
import static org.omnifaces.eleos.config.helper.LogManager.RES_BUNDLE;

import java.security.Permission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.omnifaces.eleos.config.factory.file.AuthConfigProviderEntry;
import org.omnifaces.eleos.config.factory.file.RegStoreFileParser;
import org.omnifaces.eleos.config.helper.OperationLock;

import jakarta.security.auth.message.config.AuthConfigFactory;
import jakarta.security.auth.message.config.AuthConfigProvider;
import jakarta.security.auth.message.config.RegistrationListener;
import jakarta.security.auth.message.module.ServerAuthModule;

/**
 * This class implements methods in the abstract class AuthConfigFactory.
 *
 * @author Shing Wai Chan
 */
public abstract class BaseAuthConfigFactory extends AuthConfigFactory {

    private static final Logger logger = Logger.getLogger(JASPIC_LOGGER, RES_BUNDLE);

    private static final ReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);

    private static final OperationLock operationLock = new OperationLock(readWriteLock);

    private static Map<String, AuthConfigProvider> idToProviderMap;
    private static Map<String, RegistrationContext> idToRegistrationContextMap;
    private static Map<String, List<RegistrationListener>> idToRegistrationListenersMap;
    private static Map<AuthConfigProvider, List<String>> providerToIdsMap;

    protected static final String CONF_FILE_NAME = "auth.conf";

    /**
     * Get a registered AuthConfigProvider from the factory.
     *
     * Get the provider of ServerAuthConfig and/or ClientAuthConfig objects registered for the identified message layer and
     * application context.
     *
     * <p>
     * All factories shall employ the following precedence rules to select the registered AuthConfigProvider that matches
     * (via matchConstructors) the layer and appContext arguments:
     *
     * <ul>
     *  <li>The provider that is specifically registered for both the corresponding message layer and appContext shall be
     *      selected.
     *   <li>if no provider is selected according to the preceding rule, the provider specifically registered for the
     *       corresponding appContext and for all message layers shall be selected.
     *   <li>if no provider is selected according to the preceding rules, the provider specifically registered for the
     *       corresponding message layer and for all appContexts shall be selected.
     *   <li>if no provider is selected according to the preceding rules, the provider registered for all message layers and
     *       for all appContexts shall be selected.
     *   <li>if no provider is selected according to the preceding rules, the factory shall terminate its search for a
     *       registered provider.
     * </ul>
     *
     * @param layer a String identifying the message layer for which the registered AuthConfigProvider is to be returned.
     * This argument may be null.
     *
     * @param appContext a String that identifies the application messaging context for which the registered
     * AuthConfigProvider is to be returned. This argument may be null.
     *
     * @param listener the RegistrationListener whose <code>notify</code> method is to be invoked if the corresponding
     * registration is unregistered or replaced. The value of this argument may be null.
     *
     * @return the implementation of the AuthConfigProvider interface registered at the factory for the layer and appContext
     * or null if no AuthConfigProvider is selected.
     *
     */
    @Override
    public AuthConfigProvider getConfigProvider(String layer, String appContext, RegistrationListener listener) {
        if (listener == null) {
            return doReadLocked(() -> getConfigProviderUnderLock(layer, appContext, null));
        }

        return doWriteLocked(() -> getConfigProviderUnderLock(layer, appContext, listener));
    }

    /**
     * Registers within the factory, a provider of ServerAuthConfig and/or ClientAuthConfig objects for a message layer and
     * application context identifier.
     *
     * <P>
     * At most one registration may exist within the factory for a given combination of message layer and appContext. Any
     * pre-existing registration with identical values for layer and appContext is replaced by a subsequent registration.
     * When replacement occurs, the registration identifier, layer, and appContext identifier remain unchanged, and the
     * AuthConfigProvider (with initialization properties) and description are replaced.
     *
     * <p>
     * Within the lifetime of its Java process, a factory must assign unique registration identifiers to registrations, and
     * must never assign a previously used registration identifier to a registration whose message layer and or appContext
     * identifier differ from the previous use.
     *
     * <p>
     * Programmatic registrations performed via this method must update (according to the replacement rules described
     * above), the persistent declarative representation of provider registrations employed by the factory constructor.
     *
     * @param className the fully qualified name of an AuthConfigProvider implementation class. This argument must not be
     * null.
     *
     * @param properties a Map object containing the initialization properties to be passed to the provider constructor.
     * This argument may be null. When this argument is not null, all the values and keys occuring in the Map must be of
     * type String.
     *
     * @param layer a String identifying the message layer for which the provider will be registered at the factory. A null
     * value may be passed as an argument for this parameter, in which case, the provider is registered at all layers.
     *
     * @param appContext a String value that may be used by a runtime to request a configuration object from this provider.
     * A null value may be passed as an argument for this parameter, in which case, the provider is registered for all
     * configuration ids (at the indicated layers).
     *
     * @param description a text String describing the provider. this value may be null.
     *
     * @return a String identifier assigned by the factory to the provider registration, and that may be used to remove the
     * registration from the provider.
     *
     * @exception SecurityException if the caller does not have permission to register a provider at the factory.
     */
    @Override
    @SuppressWarnings("unchecked")
    public String registerConfigProvider(String className, @SuppressWarnings("rawtypes") Map properties, String layer, String appContext,
            String description) {
        tryCheckPermission(providerRegistrationSecurityPermission);

        return _register(_constructProvider(className, properties, null), properties, layer, appContext, description, true);
    }

    @Override
    public String registerConfigProvider(AuthConfigProvider provider, String layer, String appContext, String description) {
        tryCheckPermission(providerRegistrationSecurityPermission);

        return _register(provider, null, layer, appContext, description, false);
    }

    /**
     * Remove the identified provider registration from the factory and invoke any listeners associated with the removed
     * registration.
     *
     * @param registrationID a String that identifies a provider registration at the factory
     *
     * @return true if there was a registration with the specified identifier and it was removed. Return false if the
     * registraionID was invalid.
     *
     * @exception SecurityException if the caller does not have permission to unregister the provider at the factory.
     *
     */
    @Override
    public boolean removeRegistration(String registrationID) {
        tryCheckPermission(AuthConfigFactory.providerRegistrationSecurityPermission);

        return _unRegister(registrationID);
    }

    /**
     * Disassociate the listener from all the provider registrations whose layer and appContext values are matched by the
     * corresponding arguments to this method.
     *
     * @param listener the RegistrationListener to be detached.
     *
     * @param layer a String identifying the message layer or null.
     *
     * @param appContext a String value identifying the application context or null.
     *
     * @return an array of String values where each value identifies a provider registration from which the listener was
     * removed. This method never returns null; it returns an empty array if the listener was not removed from any
     * registrations.
     *
     * @exception SecurityException if the caller does not have permission to detach the listener from the factory.
     *
     */
    @Override
    public String[] detachListener(RegistrationListener listener, String layer, String appContext) {
        tryCheckPermission(providerRegistrationSecurityPermission);

        List<String> removedListenerIds = new ArrayList<String>();
        String registrationId = getRegistrationID(layer, appContext);

        doWriteLocked(() -> {
            for (Entry<String, List<RegistrationListener>> entry : idToRegistrationListenersMap.entrySet()) {
                String targetID = entry.getKey();
                if (regIdImplies(registrationId, targetID)) {
                    List<RegistrationListener> listeners = entry.getValue();
                    if (listeners != null && listeners.remove(listener)) {
                        removedListenerIds.add(targetID);
                    }
                }
            }
        });

        return toArray(removedListenerIds);
    }

    /**
     * Get the registration identifiers for all registrations of the provider instance at the factory.
     *
     * @param configProvider the AuthConfigurationProvider whose registration identifiers are to be returned. This argument may be
     * null, in which case, it indicates that the the id's of all active registration within the factory are returned.
     *
     * @return an array of String values where each value identifies a provider registration at the factory. This method
     * never returns null; it returns an empty array when their are no registrations at the factory for the identified
     * provider.
     */
    @Override
    public String[] getRegistrationIDs(AuthConfigProvider configProvider) {
        return doReadLocked(() -> {
            Collection<String> registrationIDs = null;

            if (configProvider != null) {
                registrationIDs = providerToIdsMap.get(configProvider);
            } else {
                Collection<List<String>> collList = providerToIdsMap.values();
                if (collList != null) {
                    registrationIDs = new HashSet<String>();
                    for (List<String> listIds : collList) {
                        if (listIds != null) {
                            registrationIDs.addAll(listIds);
                        }
                    }
                }
            }

            return registrationIDs != null ? toArray(registrationIDs) : new String[0];
        });
    }

    /**
     * Get the the registration context for the identified registration.
     *
     * @param registrationID a String that identifies a provider registration at the factory
     *
     * @return a RegistrationContext or null. When a Non-null value is returned, it is a copy of the registration context
     * corresponding to the registration. Null is returned when the registration identifier does not correspond to an active
     * registration
     */
    @Override
    public RegistrationContext getRegistrationContext(String registrationID) {
        return doReadLocked(() -> idToRegistrationContextMap.get(registrationID));
    }

    /**
     * Cause the factory to reprocess its persistent declarative representation of provider registrations.
     *
     * <p>
     * A factory should only replace an existing registration when a change of provider implementation class or
     * initialization properties has occurred.
     *
     * @exception SecurityException if the caller does not have permission to refresh the factory.
     */
    @Override
    public void refresh() {
        tryCheckPermission(AuthConfigFactory.providerRegistrationSecurityPermission);

        Map<String, List<RegistrationListener>> preExistingListenersMap = doWriteLocked(() -> loadFactory());

        // Notify pre-existing listeners after (re)loading factory
        if (preExistingListenersMap != null) {
            notifyListeners(preExistingListenersMap);
        }
    }

    @Override
    public String registerServerAuthModule(ServerAuthModule serverAuthModule, Object context) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void removeServerAuthModule(Object context) {
        // TODO Auto-generated method stub

    }

    abstract protected RegStoreFileParser getRegStore();

    private AuthConfigProvider getConfigProviderUnderLock(String layer, String appContext, RegistrationListener listener) {
        AuthConfigProvider provider = null;
        String registrationID = getRegistrationID(layer, appContext);

        boolean providerFound = false;
        if (idToProviderMap.containsKey(registrationID)) {
            provider = idToProviderMap.get(registrationID);
            providerFound = true;
        }

        if (!providerFound) {
            String matchedID = getRegistrationID(null, appContext);
            if (idToProviderMap.containsKey(matchedID)) {
                provider = idToProviderMap.get(matchedID);
                providerFound = true;
            }
        }

        if (!providerFound) {
            String matchedID = getRegistrationID(layer, null);
            if (idToProviderMap.containsKey(matchedID)) {
                provider = idToProviderMap.get(matchedID);
                providerFound = true;
            }
        }

        if (!providerFound) {
            String matchedID = getRegistrationID(null, null);
            if (idToProviderMap.containsKey(matchedID)) {
                provider = idToProviderMap.get(matchedID);
            }
        }

        if (listener != null) {
            List<RegistrationListener> listeners = idToRegistrationListenersMap.computeIfAbsent(
                    registrationID, e -> new ArrayList<RegistrationListener>());

            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }

        return provider;
    }

    private static String getRegistrationID(String layer, String appContext) {

        // __0 (null, null)
        // __1<appContext> (null, appContext)
        // __2<layer> (layer, null)
        // __3<nn>_<layer><appContext> (layer, appContext)

        if (layer != null) {
            return appContext != null ? "__3" + layer.length() + "_" + layer + appContext : "__2" + layer;
        }

        return appContext != null ? "__1" + appContext : "__0";
    }

    /**
     * This API decomposes the given registration ID into layer and appContext.
     *
     * @param registrationId
     * @return a String array with layer and appContext
     */
    private static String[] decomposeRegistrationId(String registrationId) {
        String layer = null;
        String appContext = null;

        if (registrationId.equals("__0")) {
            // null, null
        } else if (registrationId.startsWith("__1")) {
            appContext = (registrationId.length() == 3) ? "" : registrationId.substring(3);
        } else if (registrationId.startsWith("__2")) {
            layer = (registrationId.length() == 3) ? "" : registrationId.substring(3);
        } else if (registrationId.startsWith("__3")) {
            int ind = registrationId.indexOf('_', 3);
            if (registrationId.length() > 3 && ind > 0) {
                int layerLength = stringToInt(registrationId.substring(3, ind));

                layer = registrationId.substring(ind + 1, ind + 1 + layerLength);
                appContext = registrationId.substring(ind + 1 + layerLength);
            } else {
                throw new IllegalArgumentException();
            }
        } else {
            throw new IllegalArgumentException();
        }

        return new String[] { layer, appContext };
    }

    private static AuthConfigProvider _constructProvider(String className, Map<String, String> properties, AuthConfigFactory factory) {
        AuthConfigProvider provider = null;

        if (className != null) {
            try {
                provider = (AuthConfigProvider) Class.forName(className, true, Thread.currentThread().getContextClassLoader())
                        .getConstructor(Map.class, AuthConfigFactory.class)
                        .newInstance(new Object[] { properties, factory });
            } catch (Throwable t) {
                Throwable cause = t.getCause();
                logger.log(WARNING, "jaspic.factory_unable_to_load_provider",
                        new Object[] { className, t.toString(), cause == null ? "cannot determine" : cause.toString() });
            }
        }

        return provider;
    }

    // XXX need to update persistent state and notify effected listeners
    private String _register(AuthConfigProvider provider, Map<String, String> properties, String layer, String appContext, String description, boolean persistent) {
        String registrationId = getRegistrationID(layer, appContext);
        RegistrationContext registrationContext = new RegistrationContextImpl(layer, appContext, description, persistent);

        Map<String, List<RegistrationListener>> listenerMap = doWriteLocked(
                () -> register(provider, properties, persistent, registrationId, registrationContext));

        // Outside write lock to prevent dead lock
        notifyListeners(listenerMap);

        return registrationId;
    }

    private Map<String, List<RegistrationListener>> register(AuthConfigProvider provider, Map<String, String> properties, boolean persistent, String registrationId, RegistrationContext registrationContext) {
        RegistrationContext previousRegistrationContext = idToRegistrationContextMap.get(registrationId);
        AuthConfigProvider previousProvider = idToProviderMap.get(registrationId);

        // Handle the persistence first - so that any exceptions occur before
        // the actual registration happens
        if (persistent) {
            _storeRegistration(registrationContext, provider, properties);
        } else if (previousRegistrationContext != null && previousRegistrationContext.isPersistent()) {
            _deleteStoredRegistration(previousRegistrationContext);
        }

        if (idToProviderMap.containsKey(registrationId)) {
            List<String> previousRegistrationsIds = providerToIdsMap.get(previousProvider);
            previousRegistrationsIds.remove(registrationId);
            if (previousRegistrationsIds.isEmpty()) {
                providerToIdsMap.remove(previousProvider);
            }
        }

        idToProviderMap.put(registrationId, provider);
        idToRegistrationContextMap.put(registrationId, registrationContext);

        List<String> registrationIds = providerToIdsMap.computeIfAbsent(provider, e -> new ArrayList<String>());

        if (!registrationIds.contains(registrationId)) {
            registrationIds.add(registrationId);
        }

        return getEffectedListeners(registrationId);
    }

    // XXX need to update persistent state and notify effected listeners
    private boolean _unRegister(String registrationId) {

        Map<String, List<RegistrationListener>> effectedListeners = doWriteLocked(() -> {
            RegistrationContext registrationContext = idToRegistrationContextMap.remove(registrationId);
            boolean hasProvider = idToProviderMap.containsKey(registrationId);
            AuthConfigProvider provider = idToProviderMap.remove(registrationId);

            List<String> registrationIds = providerToIdsMap.get(provider);
            if (registrationIds != null) {
                registrationIds.remove(registrationId);
            }

            if (registrationIds == null || registrationIds.isEmpty()) {
                providerToIdsMap.remove(provider);
            }

            if (!hasProvider) {
                return null;
            }

            Map<String, List<RegistrationListener>> listeners = getEffectedListeners(registrationId);
            if (registrationContext != null && registrationContext.isPersistent()) {
                _deleteStoredRegistration(registrationContext);
            }

            return listeners;
        });

        if (effectedListeners == null) {
            return false;
        }


        // Outside write lock to prevent dead lock
        notifyListeners(effectedListeners);

        return true;
    }

    private Map<String, List<RegistrationListener>> loadFactory() {
        Map<String, List<RegistrationListener>> oldId2RegisListenersMap = idToRegistrationListenersMap;

        _loadFactory();

        return oldId2RegisListenersMap;
    }

    // ### The following methods implement the factory's persistence layer

    protected void _loadFactory() {
        try {
            initializeMaps();

            List<AuthConfigProviderEntry> persistedEntries = getRegStore().getPersistedEntries();

            for (AuthConfigProviderEntry authConfigProviderEntry : persistedEntries) {
                if (authConfigProviderEntry.isConstructorEntry()) {
                    _constructProvider(authConfigProviderEntry.getClassName(), authConfigProviderEntry.getProperties(), this);
                } else {
                    boolean first = true;
                    AuthConfigProvider configProvider = null;
                    for (RegistrationContext context : authConfigProviderEntry.getRegistrationContexts()) {
                        if (first) {
                            configProvider = _constructProvider(authConfigProviderEntry.getClassName(), authConfigProviderEntry.getProperties(), null);
                        }

                        _loadRegistration(configProvider, context.getMessageLayer(), context.getAppContext(), context.getDescription());
                    }
                }
            }
        } catch (Exception e) {
            if (logger.isLoggable(WARNING)) {
                logger.log(WARNING, "jaspic.factory_auth_config_loader_failure", e);
            }
        }
    }

    /**
     * Initialize the static maps in a static method
     */
    private static void initializeMaps() {
        idToProviderMap = new HashMap<String, AuthConfigProvider>();
        idToRegistrationContextMap = new HashMap<String, RegistrationContext>();
        idToRegistrationListenersMap = new HashMap<String, List<RegistrationListener>>();
        providerToIdsMap = new HashMap<AuthConfigProvider, List<String>>();
    }

    private static String _loadRegistration(AuthConfigProvider provider, String layer, String appContext, String description) {

        RegistrationContext registrationContext = new RegistrationContextImpl(layer, appContext, description, true);
        String registrationId = getRegistrationID(layer, appContext);

        AuthConfigProvider previousProvider = idToProviderMap.get(registrationId);

        boolean wasRegistered = idToProviderMap.containsKey(registrationId);
        if (wasRegistered) {
            List<String> previousRegistrationIds = providerToIdsMap.get(previousProvider);
            previousRegistrationIds.remove(registrationId);
            if (previousRegistrationIds.isEmpty()) {
                providerToIdsMap.remove(previousProvider);
            }
        }

        idToProviderMap.put(registrationId, provider);
        idToRegistrationContextMap.put(registrationId, registrationContext);

        List<String> registrationIds = providerToIdsMap.get(provider);
        if (registrationIds == null) {
            registrationIds = new ArrayList<String>();
            providerToIdsMap.put(provider, registrationIds);
        }

        if (!registrationIds.contains(registrationId)) {
            registrationIds.add(registrationId);
        }

        return registrationId;
    }

    private void _storeRegistration(RegistrationContext registrationContext, AuthConfigProvider configProvider,
            Map<String, String> properties) {
        String className = null;
        if (configProvider != null) {
            className = configProvider.getClass().getName();
        }

        if (propertiesContainAnyNonStringValues(properties)) {
            throw new IllegalArgumentException("AuthConfigProvider cannot be registered - properties must all be of type String.");
        }

        if (registrationContext.isPersistent()) {
            getRegStore().store(className, registrationContext, properties);
        }
    }

    private boolean propertiesContainAnyNonStringValues(Map<String, String> properties) {
        if (properties != null) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (!(entry.getValue() instanceof String)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void _deleteStoredRegistration(RegistrationContext registrationContext) {
        if (registrationContext.isPersistent()) {
            getRegStore().delete(registrationContext);
        }
    }

    private static boolean regIdImplies(String reference, String target) {

        boolean rvalue = true;

        String[] refID = decomposeRegistrationId(reference);
        String[] targetID = decomposeRegistrationId(target);

        if (refID[0] != null && !refID[0].equals(targetID[0])) {
            rvalue = false;
        } else if (refID[1] != null && !refID[1].equals(targetID[1])) {
            rvalue = false;
        }

        return rvalue;
    }

    /**
     * Will return some extra listeners. In other words, effected listeners could be reduced by removing any associated with
     * a provider registration id that is more specific than the one being added or removed.
     */
    private static Map<String, List<RegistrationListener>> getEffectedListeners(String regisID) {
        Map<String, List<RegistrationListener>> effectedListeners = new HashMap<String, List<RegistrationListener>>();
        Set<String> listenerRegistrations = new HashSet<String>(idToRegistrationListenersMap.keySet());

        for (String listenerID : listenerRegistrations) {
            if (regIdImplies(regisID, listenerID)) {
                if (!effectedListeners.containsKey(listenerID)) {
                    effectedListeners.put(listenerID, new ArrayList<RegistrationListener>());
                }
                effectedListeners.get(listenerID).addAll(idToRegistrationListenersMap.remove(listenerID));
            }
        }
        return effectedListeners;
    }

    private static void tryCheckPermission(Permission permission) {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            securityManager.checkPermission(permission);
        }
    }

    protected <T> T doReadLocked(Supplier<T> supplier) {
        return operationLock.doReadLocked(supplier);
    }

    protected <T> T doWriteLocked(Supplier<T> supplier) {
        return operationLock.doWriteLocked(supplier);
    }

    protected void doWriteLocked(Runnable runnable) {
       operationLock.doWriteLocked(runnable);
    }

    private String[] toArray(Collection<String> collection) {
        return collection.toArray(new String[collection.size()]);
    }

    private static int stringToInt(String numberString) {
        try {
            return Integer.parseInt(numberString);
        } catch (Exception ex) {
            throw new IllegalArgumentException();
        }
    }

    private static void notifyListeners(Map<String, List<RegistrationListener>> map) {
        Set<Map.Entry<String, List<RegistrationListener>>> entrySet = map.entrySet();
        for (Map.Entry<String, List<RegistrationListener>> entry : entrySet) {
            List<RegistrationListener> listeners = map.get(entry.getKey());

            if (listeners != null && listeners.size() > 0) {
                String[] dIds = decomposeRegistrationId(entry.getKey());

                for (RegistrationListener listener : listeners) {
                    listener.notify(dIds[0], dIds[1]);
                }
            }
        }
    }
}
