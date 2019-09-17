package org.omnifaces.elios.data;

import java.util.HashMap;

public class AuthModulesLayerConfig {
    public String defaultClientID;
    public String defaultServerID;
    public HashMap idMap;

    public AuthModulesLayerConfig(String defaultClientID, String defaultServerID, HashMap idMap) {
        this.defaultClientID = defaultClientID;
        this.defaultServerID = defaultServerID;
        this.idMap = idMap;
    }

    public HashMap getIdMap() {
        return idMap;
    }

    public void setIdMap(HashMap map) {
        idMap = map;
    }

    public String getDefaultClientID() {
        return defaultClientID;
    }

    public String getDefaultServerID() {
        return defaultServerID;
    }
}
