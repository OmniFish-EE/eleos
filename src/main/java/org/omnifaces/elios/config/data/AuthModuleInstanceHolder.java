package org.omnifaces.elios.config.data;

import java.util.Map;

/**
 * A data object contains module object and the corresponding map.
 */
public class AuthModuleInstanceHolder {
    private Object module;
    private Map map;

    public AuthModuleInstanceHolder(Object module, Map map) {
        this.module = module;
        this.map = map;
    }

    public Object getModule() {
        return module;
    }

    public Map getMap() {
        return map;
    }
}