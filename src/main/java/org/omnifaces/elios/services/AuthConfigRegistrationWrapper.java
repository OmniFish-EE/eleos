package org.omnifaces.elios.services;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.security.auth.message.config.RegistrationListener;

public class AuthConfigRegistrationWrapper {

    private String layer;
    private String appCtxt;
    private String jmacProviderRegisID = null;
    private boolean enabled;
    private ConfigData data;

    private Lock wLock;
    private ReadWriteLock rwLock;

    AuthConfigRegistrationListener listener;
    int referenceCount = 1;

    public AuthConfigRegistrationWrapper(String layer, String appCtxt) {
        this.layer = layer;
        this.appCtxt = appCtxt;
        this.rwLock = new ReentrantReadWriteLock(true);
        this.wLock = rwLock.writeLock();
        enabled = (factory != null);
        listener = new AuthConfigRegistrationListener(layer, appCtxt);
    }

    public AuthConfigRegistrationListener getListener() {
        return listener;
    }

    public void setListener(AuthConfigRegistrationListener listener) {
        this.listener = listener;
    }

    public void disable() {
        this.wLock.lock();
        try {
            setEnabled(false);
        } finally {
            this.wLock.unlock();
            data = null;
        }
        if (factory != null) {
            String[] ids = factory.detachListener(this.listener, layer, appCtxt);
//                if (ids != null) {
//                    for (int i=0; i < ids.length; i++) {
//                        factory.removeRegistration(ids[i]);
//                    }
//                }
            if (getJmacProviderRegisID() != null) {
                factory.removeRegistration(getJmacProviderRegisID());
            }
        }
    }

    // detach the listener, but dont remove-registration
    public void disableWithRefCount() {
        if (referenceCount <= 1) {
            disable();
        } else {
            try {
                this.wLock.lock();
                referenceCount--;
            } finally {
                this.wLock.unlock();
            }

        }
    }

    public void incrementReference() {
        try {
            this.wLock.lock();
            referenceCount++;
        } finally {
            this.wLock.unlock();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getJmacProviderRegisID() {
        return this.jmacProviderRegisID;
    }

    public void setJmacProviderRegisID(String jmacProviderRegisID) {
        this.jmacProviderRegisID = jmacProviderRegisID;
    }

    ConfigData getConfigData() {
        return data;
    }

    void setConfigData(ConfigData data) {
        this.data = data;
    }
    
    public class AuthConfigRegistrationListener implements RegistrationListener {

        private String layer;
        private String appCtxt;

        public AuthConfigRegistrationListener(String layer, String appCtxt) {
            this.layer = layer;
            this.appCtxt = appCtxt;
        }

        public void notify(String layer, String appContext) {
            if (this.layer.equals(layer) && ((this.appCtxt == null && appContext == null) || (appContext != null && appContext.equals(this.appCtxt)))) {
                try {
                    wLock.lock();
                    data = null;
                } finally {
                    wLock.unlock();
                }
            }
        }

    }

}
