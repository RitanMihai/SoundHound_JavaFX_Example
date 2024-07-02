package com.example.soundhounddemo;

import com.Hound.HoundJSON.ConversationStateJSON;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public class SessionManager {
    private static SessionManager instance;
    private String clientId;
    private String clientKey;
    private String username;
    private Set<String> sessionIds;
    private String currentSessionId;
    private ConversationStateJSON conversationState;

    private SessionManager() {
        sessionIds = new HashSet<>();
    }

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public void initialize(String clientId, String clientKey, String username) {
        this.clientId = clientId;
        this.clientKey = clientKey;
        this.username = username;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientKey() {
        return clientKey;
    }

    public String getUsername() {
        return username;
    }

    // Session ID management
    public String getCurrentSessionId() {
        return currentSessionId;
    }

    public void addSessionId(String sessionId) {
        if (sessionIds.add(sessionId)) {
            currentSessionId = sessionId;
        }
    }

    public void setCredentials(String clientId, String clientKey, String username) {
        this.clientId = clientId;
        this.clientKey = clientKey;
        this.username = username;
    }

    // Conversation state management
    public ConversationStateJSON getConversationState() {
        return conversationState;
    }

    public void setConversationState(ConversationStateJSON conversationState) {
        this.conversationState = conversationState;
    }
}
