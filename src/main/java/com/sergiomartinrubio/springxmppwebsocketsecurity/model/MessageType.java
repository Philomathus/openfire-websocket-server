package com.sergiomartinrubio.springxmppwebsocketsecurity.model;

public enum MessageType {
    //request
    PERSONAL_MESSAGE, GROUP_MESSAGE, CREATE_GROUP, JOIN_GROUP, LEAVE_GROUP, LOG_OUT,
    //response
    LOGGED_IN, ERROR, FORBIDDEN
}
