package com.feiwin.openfirewebsocketserver.model;

public enum MessageType {
    //request
    PERSONAL_MESSAGE, ROOM_MESSAGE, CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, DESTROY_ROOM, LOGOUT,
    //response
    SUCCESS, FAIL
}
