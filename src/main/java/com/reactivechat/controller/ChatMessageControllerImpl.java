package com.reactivechat.controller;

import com.reactivechat.model.Contact;
import com.reactivechat.model.Group;
import com.reactivechat.model.User;
import com.reactivechat.model.message.ChatMessage;
import com.reactivechat.model.message.ChatMessage.DestinationType;
import com.reactivechat.model.message.MessageType;
import com.reactivechat.model.message.ResponseMessage;
import com.reactivechat.repository.GroupsRepository;
import com.reactivechat.repository.SessionsRepository;
import com.reactivechat.repository.UsersRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static com.reactivechat.model.message.MessageType.CONTACTS_LIST;
import static com.reactivechat.model.message.MessageType.NEW_CONTACT_REGISTERED;

@Service
public class ChatMessageControllerImpl implements ChatMessageController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatMessageControllerImpl.class);
    
    private final UsersRepository usersRepository;
    private final GroupsRepository groupsRepository;
    private final SessionsRepository sessionsRepository;
    private final MessageBroadcasterController broadcasterController;
    
    public ChatMessageControllerImpl(final UsersRepository usersRepository,
                                     final GroupsRepository groupsRepository,
                                     final SessionsRepository sessionsRepository,
                                     final MessageBroadcasterController broadcasterController) {
        
        this.usersRepository = usersRepository;
        this.groupsRepository = groupsRepository;
        this.sessionsRepository = sessionsRepository;
        this.broadcasterController = broadcasterController;
    }
    
    @Override
    public void handleChatMessage(final Session session,
                                  final ChatMessage chatMessage) {
        
        final User user = sessionsRepository.findBySessionId(session.getId());

        if (chatMessage.getDestinationType() == DestinationType.USER) {
            final User destinationUser = usersRepository.findById(chatMessage.getDestinationId());
            LOGGER.info("Messaged received from user {} to user {}", user.getUsername(), destinationUser.getUsername());
        } else if (chatMessage.getDestinationType() == DestinationType.ALL_USERS_GROUP) {
            LOGGER.info("Messaged received from user {} to all users", user.getName());
        }
    
        ResponseMessage<ChatMessage> responseMessage = new ResponseMessage<>(MessageType.USER_MESSAGE, chatMessage);

        broadcasterController.broadcastChatMessage(session, responseMessage);
        
    }
    
    @Override
    public void handleContactsMessage(final Session session) {
    
        final User user = sessionsRepository.findBySessionId(session.getId());
        
        final List<User> userContacts = usersRepository.findContacts(user);
        final List<Group> groupContacts = groupsRepository.findGroups(user);
        final List<Contact> allContacts = new ArrayList<>(groupContacts);
        allContacts.addAll(userContacts);
    
        ResponseMessage<Object> responseMessage = ResponseMessage
            .builder()
            .type(CONTACTS_LIST)
            .payload(allContacts)
            .build();
        
        broadcasterController.broadcastToSession(session, responseMessage);
    }
    
    @Override
    public void handleNewContact(final Contact contact, final Session session) {
    
        ResponseMessage<Object> responseMessage = ResponseMessage
            .builder()
            .type(NEW_CONTACT_REGISTERED)
            .payload(Collections.singletonList(contact))
            .build();
    
        broadcasterController.broadcastToAllExceptSession(session, responseMessage);
        
    }
    
}
