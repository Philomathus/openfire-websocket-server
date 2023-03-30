package com.sergiomartinrubio.springxmppwebsocketsecurity.xmpp;

import com.sergiomartinrubio.springxmppwebsocketsecurity.exception.XmppGenericException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.PresenceBuilder;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.iqregister.AccountManager;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.xdata.form.FillableForm;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.websocket.Session;
import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(XmppProperties.class)
public class XmppClient {

    private final XmppProperties xmppProperties;
    private final XmppToWebSocketTransmitter xmppToWebSocketTransmitter;
    private XMPPTCPConnection adminXmppConnection;

    @PostConstruct
    private void loginAdminXmppAccount() {
        adminXmppConnection = connect(xmppProperties.getAdminUsername(), xmppProperties.getAdminPassword());
        login(adminXmppConnection); //always login after connecting
    }

    /*
    * String groupId = "global";
        MultiUserChatManager multiUserChatManager = MultiUserChatManager.getInstanceFor(adminXmppConnection);
        MultiUserChat multiUserChat = null;

        try {
            multiUserChat = multiUserChatManager.getMultiUserChat(JidCreate.entityBareFrom(groupId + "@" + xmppProperties.getGroupDomain()));
        } catch (XmppStringprepException e) {
            e.printStackTrace();
        }

        try {
            System.out.println("Before: " + multiUserChat.isJoined());
            multiUserChat.join( adminXmppConnection.getUser().getResourcepart() );
            System.out.println("After: " + multiUserChat.isJoined());
            multiUserChat.leave(); //to prevent the zombie members from accumulating, make sure to leave
            System.out.println("Test leave: " + multiUserChat.isJoined());
        } catch (Exception e) {
            e.printStackTrace();
        }
    *
    * */

    public XMPPTCPConnection connect(String username, String password) {
        XMPPTCPConnection connection;
        try {
            EntityBareJid entityBareJid;
            entityBareJid = JidCreate.entityBareFrom(username + "@" + xmppProperties.getDomain());
            XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                    .setHost(xmppProperties.getHost())
                    .setPort(xmppProperties.getPort())
                    .setXmppDomain(xmppProperties.getDomain())
                    .setUsernameAndPassword(entityBareJid.getLocalpart(), password)
                    .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                    .setResource(entityBareJid.getResourceOrEmpty())
                    .setSendPresence(true)
                    .build();

            connection = new XMPPTCPConnection(config);
            connection.connect();
        } catch (SmackException | IOException | XMPPException | InterruptedException e) {
            log.info("Could not connect to XMPP server.", e);
            return null;
        }
        return connection;
    }

    public void createAccount(String username, String password) {
        AccountManager accountManager = AccountManager.getInstance(adminXmppConnection);
        accountManager.sensitiveOperationOverInsecureConnection(true);
        try {
            accountManager.createAccount(Localpart.from(username), password);
        } catch (SmackException.NoResponseException |
                XMPPException.XMPPErrorException |
                SmackException.NotConnectedException |
                InterruptedException |
                XmppStringprepException e) {
            throw new XmppGenericException(adminXmppConnection.getUser().toString(), e);
        }

        log.info("Account for user '{}' created.", username);
    }

    public void login(XMPPTCPConnection connection) {
        try {
            connection.login();
        } catch (XMPPException | SmackException | IOException | InterruptedException e) {
            log.error("Login to XMPP server with user {} failed.", connection.getUser(), e);

            EntityFullJid user = connection.getUser();
            throw new XmppGenericException(user == null ? "unknown" : user.toString(), e);
        }
        log.info("User '{}' logged in.", connection.getUser());
    }

    public void addIncomingMessageListener(XMPPTCPConnection connection, Session webSocketSession) {
        ChatManager chatManager = ChatManager.getInstanceFor(connection);
        chatManager.addIncomingListener((from, message, chat) -> xmppToWebSocketTransmitter
                .sendPersonalResponse(message, webSocketSession));
        log.info("Incoming message listener for user '{}' added.", connection.getUser());
    }

//    public void addIncomingGroupMessageListener(XMPPTCPConnection connection, Session webSocketSession, String groupId) {
//        MultiUserChatManager multiUserChatManager = MultiUserChatManager.getInstanceFor(connection);
//
//        for(EntityBareJid jid : multiUserChatManager.getJoinedRooms()) {
//            MultiUserChat multiUserChat = multiUserChatManager.getMultiUserChat(jid);
//            multiUserChat.addMessageListener( message -> xmppToWebSocketTransmitter
//                    .sendGroupResponse(message, webSocketSession) );
//        }
//
//        log.info("Incoming group message listener for user '{}' added.", connection.getUser());
//    }

    public void joinGroup(XMPPTCPConnection connection, Session webSocketSession, String groupId) {
        try {
            MultiUserChat multiUserChat = MultiUserChatManager.getInstanceFor(connection)
                    .getMultiUserChat(JidCreate.entityBareFrom(groupId + "@" + xmppProperties.getGroupDomain()));
            multiUserChat.join(connection.getUser().getResourcepart());
            multiUserChat.addMessageListener( message -> xmppToWebSocketTransmitter
                    .sendGroupResponse(message, webSocketSession) );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void createGroup(XMPPTCPConnection connection, Session webSocketSession, String groupId) {
        try {
            MultiUserChat multiUserChat = MultiUserChatManager.getInstanceFor(connection)
                    .getMultiUserChat(JidCreate.entityBareFrom(groupId + "@" + xmppProperties.getGroupDomain()));
            multiUserChat.create(connection.getUser().getResourcepart());
            FillableForm form = multiUserChat.getConfigurationForm().getFillableForm();
            form.setAnswer("muc#roomconfig_persistentroom", true);
            form.setAnswer("muc#roomconfig_maxusers", 100);
            multiUserChat.sendConfigurationForm(form);
            multiUserChat.addMessageListener( message -> xmppToWebSocketTransmitter
                    .sendGroupResponse(message, webSocketSession) );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(XMPPTCPConnection connection, String message, String to) {
        ChatManager chatManager = ChatManager.getInstanceFor(connection);
        try {
            Chat chat = chatManager.chatWith(JidCreate.entityBareFrom(to + "@" + xmppProperties.getDomain()));
            chat.send(message);
            log.info("Message sent to user '{}' from user '{}'.", to, connection.getUser());
        } catch (XmppStringprepException | SmackException.NotConnectedException | InterruptedException e) {
            throw new XmppGenericException(connection.getUser().toString(), e);
        }
    }

    public void sendGroupMessage(XMPPTCPConnection connection, String message, String groupId) {
        try {
            MultiUserChat multiUserChat = MultiUserChatManager.getInstanceFor(connection)
                    .getMultiUserChat(JidCreate.entityBareFrom(groupId + "@" + xmppProperties.getGroupDomain()));
            multiUserChat.sendMessage(message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void leaveGroup(XMPPTCPConnection connection, String groupId) {
        try {
            MultiUserChat multiUserChat = MultiUserChatManager.getInstanceFor(connection)
                    .getMultiUserChat(JidCreate.entityBareFrom(groupId + "@" + xmppProperties.getGroupDomain()));
            multiUserChat.leave();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void destroyRoom(XMPPTCPConnection connection, String groupId) {
        try {
            MultiUserChat multiUserChat = MultiUserChatManager.getInstanceFor(connection)
                    .getMultiUserChat(JidCreate.entityBareFrom(groupId + '@' + xmppProperties.getGroupDomain()));
            multiUserChat.destroy("The owner left", null);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void disconnect(XMPPTCPConnection connection) {
        Presence presence = PresenceBuilder.buildPresence()
                .ofType(Presence.Type.unavailable)
                .build();
        try {
            connection.sendStanza(presence);
            connection.disconnect();
        } catch (SmackException.NotConnectedException | InterruptedException e) {
            log.error("XMPP error.", e);

        }
        connection.disconnect();
        log.info("Connection closed for user '{}'.", connection.getUser());
    }

    public void sendStanza(XMPPTCPConnection connection, Presence.Type type) {
        Presence presence = PresenceBuilder.buildPresence()
                .ofType(type)
                .build();
        try {
            connection.sendStanza(presence);
            log.info("Status {} sent for user '{}'.", type, connection.getUser());
        } catch (SmackException.NotConnectedException | InterruptedException e) {
            log.error("XMPP error.", e);
            throw new XmppGenericException(connection.getUser().toString(), e);
        }
    }
}
