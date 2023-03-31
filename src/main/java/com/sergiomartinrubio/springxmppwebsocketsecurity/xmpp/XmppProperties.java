package com.sergiomartinrubio.springxmppwebsocketsecurity.xmpp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A connection contains common information needed to connect to an XMPP server
 * and sign in.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "xmpp")
public class XmppProperties {

    /**
     * The address of the server.
     */
    private String host;

    /**
     * The port to use (usually 5222).
     */
    private int port;

    /**
     * The XMPP domain is what follows after the '@' sign in XMPP addresses (JIDs).
     */
    private String domain;

    private String roomDomain;

    private String adminUsername;

    private String adminPassword;

    /**
     * The placeholder password for all XMPP accounts.
     */
    private String userPassword;

}
