package org.omnifaces.elios.config.delegate;

import static javax.security.auth.message.MessagePolicy.ProtectionPolicy.AUTHENTICATE_SENDER;

import java.util.Map;

import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.MessagePolicy;
import javax.security.auth.message.MessagePolicy.TargetPolicy;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ServletMessagePolicyDelegate implements MessagePolicyDelegate {

    private static final String MANDATORY_AUTH_CONTEXT_ID = "mandatory";
    private static final String OPTIONAL_AUTH_CONTEXT_ID = "optional";
    private static final String MANDATORY_KEY = "javax.security.auth.message.MessagePolicy.isMandatory";

    private static final Class<?>[] MESSAGE_TYPES = new Class[] { HttpServletRequest.class, HttpServletResponse.class };

    private static final MessagePolicy mandatoryPolicy = new MessagePolicy(new TargetPolicy[] { new TargetPolicy(null, () -> AUTHENTICATE_SENDER) }, true);
    private static final MessagePolicy optionalPolicy = new MessagePolicy(new TargetPolicy[] { new TargetPolicy(null, () -> AUTHENTICATE_SENDER) }, false);
    
    @Override
    public Class<?>[] getMessageTypes() {
        return MESSAGE_TYPES;
    }
    
    @Override
    public MessagePolicy getRequestPolicy(String authContextID, Map properties) {
        return MANDATORY_AUTH_CONTEXT_ID.equals(authContextID) ? mandatoryPolicy : optionalPolicy;
    }

    @Override
    public MessagePolicy getResponsePolicy(String authContextID, Map properties) {
        return null;
    }

    @Override
    public String getAuthContextID(MessageInfo messageInfo) {
        return messageInfo.getMap().containsKey(MANDATORY_KEY) ? MANDATORY_AUTH_CONTEXT_ID : OPTIONAL_AUTH_CONTEXT_ID;
    }

    @Override
    public boolean isProtected() {
        return true;
    }

};