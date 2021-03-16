package com.reactivechat.controller.impl;

import com.reactivechat.controller.BroadcasterController;
import com.reactivechat.controller.ChatMessageController;
import com.reactivechat.model.contacs.Contact;
import com.reactivechat.model.contacs.Group;
import com.reactivechat.model.contacs.User;
import com.reactivechat.model.message.ChatHistoryRequest;
import com.reactivechat.model.message.ChatHistoryResponse;
import com.reactivechat.model.message.ChatMessage;
import com.reactivechat.model.message.MessageType;
import com.reactivechat.model.message.ResponseMessage;
import com.reactivechat.model.session.ChatSession;
import com.reactivechat.repository.GroupRepository;
import com.reactivechat.repository.MessageRepository;
import com.reactivechat.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static com.reactivechat.model.message.MessageType.CHAT_HISTORY;
import static com.reactivechat.model.message.MessageType.CONTACTS_LIST;
import static com.reactivechat.model.message.MessageType.NEW_CONTACT_REGISTERED;

@Service
public class ChatMessageControllerImpl implements ChatMessageController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatMessageControllerImpl.class);

    private final ExecutorService executorService;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final MessageRepository messageRepository;
    private final BroadcasterController broadcasterController;
    
    public ChatMessageControllerImpl(final ExecutorService executorService,
                                     final UserRepository userRepository,
                                     final GroupRepository groupRepository,
                                     final MessageRepository messageRepository,
                                     final BroadcasterController broadcasterController) {
        
        this.executorService = executorService;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.messageRepository = messageRepository;
        this.broadcasterController = broadcasterController;
    }
    
    @Override
    public void handleChatMessage(final ChatSession chatSession,
                                  final ChatMessage receivedMessage) {
    
        Mono
            .fromRunnable(() -> {
    
                LOGGER.info("handling chat message");
                
                final String userId = chatSession.getUserAuthenticationDetails().getUserId();
    
                final ChatMessage chatMessage = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .from(userId)
                    .date(OffsetDateTime.now().toString())
                    .destinationId(receivedMessage.getDestinationId())
                    .destinationType(receivedMessage.getDestinationType())
                    .content(receivedMessage.getContent())
                    .mimeType(receivedMessage.getMimeType())
                    .build();
    
                ResponseMessage<ChatMessage> responseMessage = new ResponseMessage<>(MessageType.USER_MESSAGE, chatMessage);
                
                messageRepository.insert(chatMessage);
                broadcasterController.broadcastChatMessage(chatSession, responseMessage);
                
            })
            .publishOn(Schedulers.fromExecutorService(executorService))
            .subscribe();

    }
    
    @Override
    public void handleContactsMessage(final ChatSession chatSession) {
    
        final String userId = chatSession.getUserAuthenticationDetails().getUserId();
        final Flux<User> userContacts = userRepository.findContacts(userId);
        final Flux<Group> groupContacts = groupRepository.findGroups(userId);
        
        Flux.concat(userContacts, groupContacts)
            .collectList()
            .subscribe(contacts -> {

                ResponseMessage<Object> responseMessage = ResponseMessage
                    .builder()
                    .type(CONTACTS_LIST)
                    .payload(contacts)
                    .build();
    
                broadcasterController.broadcastToSession(chatSession, responseMessage);
                
            });

    }
    
    @Override
    public void handleNewContact(final Contact contact, final ChatSession chatSession) {
    
        ResponseMessage<Object> responseMessage = ResponseMessage
            .builder()
            .type(NEW_CONTACT_REGISTERED)
            .payload(Collections.singletonList(contact))
            .build();
    
        broadcasterController.broadcastToAllExceptSession(chatSession, responseMessage);
        
    }
    
    @Override
    public void handleChatHistory(final ChatSession chatSession,
                                  final ChatHistoryRequest chatHistoryRequest) {
        
        final String senderId = chatSession.getUserAuthenticationDetails().getUserId();
        
        messageRepository.findMessages(senderId, chatHistoryRequest)
            .collectList()
            .subscribe(chatHistory -> {
    
                final ResponseMessage<Object> responseMessage = ResponseMessage
                    .builder()
                    .type(CHAT_HISTORY)
                    .payload(ChatHistoryResponse.builder()
                        .destinationId(chatHistoryRequest.getDestinationId())
                        .chatHistory(chatHistory)
                        .build())
                    .build();
    
                broadcasterController.broadcastToSession(chatSession, responseMessage);
    
            });
        
    }
    
}
