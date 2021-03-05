package com.reactivechat.controller;

import com.reactivechat.exception.ChatException;
import com.reactivechat.exception.ResponseStatus;
import com.reactivechat.model.User;
import com.reactivechat.model.message.AuthenticateRequest;
import com.reactivechat.model.message.AuthenticateResponse;
import com.reactivechat.model.message.MessageType;
import com.reactivechat.model.message.ReauthenticateRequest;
import com.reactivechat.model.message.ResponseMessage;
import com.reactivechat.model.message.SignupRequest;
import com.reactivechat.repository.SessionsRepository;
import com.reactivechat.repository.UsersRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationControllerImpl implements AuthenticationController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationControllerImpl.class);
    private static final String DEFAULT_DESCRIPTION = "Hi, I'm using SocialChat!";
    
    private final UsersRepository usersRepository;
    private final SessionsRepository sessionsRepository;
    private final AvatarController avatarController;
    private final ChatMessageController chatMessageController;
    private final MessageBroadcasterController broadcasterController;
    
    @Autowired
    public AuthenticationControllerImpl(final UsersRepository usersRepository,
                                        final SessionsRepository sessionsRepository,
                                        final AvatarController avatarController,
                                        final ChatMessageController chatMessageController,
                                        final MessageBroadcasterController broadcasterController) {
        this.usersRepository = usersRepository;
        this.sessionsRepository = sessionsRepository;
        this.avatarController = avatarController;
        this.chatMessageController = chatMessageController;
        this.broadcasterController = broadcasterController;
    }
    
    @Override
    public void handleAuthenticate(final AuthenticateRequest authenticateRequest,
                                   final Session session) {
    
        try {
    
            AuthenticateResponse response = authenticate(authenticateRequest, session);
    
            final ResponseMessage<Object> responseMessage = ResponseMessage
                .builder()
                .type(MessageType.AUTHENTICATE)
                .payload(response)
                .build();
    
            broadcasterController.broadcastToSession(session, responseMessage);
            
        } catch (ChatException e) {
    
            LOGGER.error("Failed to authenticate user {}. Reason: {}", authenticateRequest.getUsername(), e.getMessage());
            
            final ResponseMessage<Object> responseMessage = ResponseMessage
                .builder()
                .type(MessageType.AUTHENTICATE)
                .payload(e.toErrorMessage())
                .build();
    
            broadcasterController.broadcastToSession(session, responseMessage);
            
        }

    }
    
    @Override
    public void handleReauthenticate(final ReauthenticateRequest reauthenticateRequest, final Session session) {
    
        try {
    
            final User user = sessionsRepository.reauthenticate(session, reauthenticateRequest.getToken());
    
            final AuthenticateResponse authenticateResponse = AuthenticateResponse.builder()
                .user(user)
                .token(reauthenticateRequest.getToken())
                .status(ResponseStatus.SUCCESS)
                .build();
    
            final ResponseMessage<Object> responseMessage = ResponseMessage
                .builder()
                .type(MessageType.REAUTHENTICATE)
                .payload(authenticateResponse)
                .build();
        
            broadcasterController.broadcastToSession(session, responseMessage);
    
            LOGGER.info("Session {} reauthenticated with token {} of user {}", session.getId(), reauthenticateRequest.getToken(), user.getUsername());
            
        } catch (ChatException e) {
        
            LOGGER.error("Failed to reauthenticate with token {}. Reason: {}", reauthenticateRequest.getToken(), e.getMessage());
            
            final ResponseMessage<Object> responseMessage = ResponseMessage
                .builder()
                .type(MessageType.REAUTHENTICATE)
                .payload(e.toErrorMessage())
                .build();
        
            broadcasterController.broadcastToSession(session, responseMessage);
        
        }
        
    }
    
    @Override
    public void handleSignup(final SignupRequest signupRequest, final Session session) {
    
        try {
    
            final User createdUser = usersRepository.create(mapToUser(signupRequest));
    
            AuthenticateRequest authenticateRequest = AuthenticateRequest.builder()
                .username(signupRequest.getUsername())
                .build();
    
            AuthenticateResponse authenticateResponse = authenticate(authenticateRequest, session);
    
            final ResponseMessage<Object> responseMessage = ResponseMessage
                .builder()
                .type(MessageType.SIGNUP)
                .payload(authenticateResponse)
                .build();
    
            broadcasterController.broadcastToSession(session, responseMessage);
            chatMessageController.handleNewContact(createdUser, session);
    
            LOGGER.info("New user registered: {}", signupRequest.getUsername());
            
        } catch (ChatException e) {
    
            LOGGER.error("Failed to create user {}. Reason: {}", signupRequest.getUsername(), e.getMessage());
    
            final ResponseMessage<Object> responseMessage = ResponseMessage
                .builder()
                .type(MessageType.SIGNUP)
                .payload(e.toErrorMessage())
                .build();
    
            broadcasterController.broadcastToSession(session, responseMessage);
            
        }
        
    }
    
    private AuthenticateResponse authenticate(final AuthenticateRequest authenticateRequest, final Session session) {
        
        Optional<User> userOpt = usersRepository.findByUsername(authenticateRequest.getUsername());
        
        if (userOpt.isPresent()) {

            try {
                
                User user = userOpt.get();
                String token = buildToken(session, user);
                sessionsRepository.create(user, session);
                sessionsRepository.authenticate(session, token);
    
                LOGGER.info("New session created: {}", session.getId());
    
                return AuthenticateResponse.builder()
                    .user(user)
                    .token(token)
                    .status(ResponseStatus.SUCCESS)
                    .build();
                
            } catch (Exception e) {
                throw new ChatException("Failed to authenticate. Reason: " + e.getMessage(), ResponseStatus.SERVER_ERROR);
            }
         
        }
        
        throw new ChatException("Invalid Credentials", ResponseStatus.INVALID_CREDENTIALS);
    }
    
    @Override
    public boolean isAuthenticatedSession(final Session session, final String token) {
        return sessionsRepository.sessionIsAuthenticated(session, token);
    }
    
    @Override
    public void logoff(final Session session) {
        sessionsRepository.delete(session);
    }
    
    private String buildToken(final Session session, final User user) {
        
        final String token = UUID.randomUUID().toString() + "-" + user.getId() + "-" + session.getId();
        
        return Base64
            .getEncoder()
            .encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }
    
    private User mapToUser(final SignupRequest signupRequest) {
        return User.builder()
            .username(signupRequest.getUsername())
            .name(signupRequest.getName())
            .avatar(avatarController.pickRandomAvatar())
            .description(DEFAULT_DESCRIPTION)
            .build();
    }
    
}
