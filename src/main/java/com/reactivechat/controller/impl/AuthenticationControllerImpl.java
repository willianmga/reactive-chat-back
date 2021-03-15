package com.reactivechat.controller.impl;

import com.reactivechat.controller.AuthenticationController;
import com.reactivechat.controller.AvatarController;
import com.reactivechat.controller.ChatMessageController;
import com.reactivechat.controller.MessageBroadcasterController;
import com.reactivechat.exception.ChatException;
import com.reactivechat.exception.ResponseStatus;
import com.reactivechat.model.User;
import com.reactivechat.model.UserDTO;
import com.reactivechat.model.message.AuthenticateRequest;
import com.reactivechat.model.message.AuthenticateResponse;
import com.reactivechat.model.message.MessageType;
import com.reactivechat.model.message.ReauthenticateRequest;
import com.reactivechat.model.message.ResponseMessage;
import com.reactivechat.model.message.SignupRequest;
import com.reactivechat.model.session.ChatSession;
import com.reactivechat.model.session.ServerDetails;
import com.reactivechat.repository.SessionRepository;
import com.reactivechat.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.reactivechat.exception.ResponseStatus.INVALID_CREDENTIALS;
import static com.reactivechat.exception.ResponseStatus.INVALID_NAME;
import static com.reactivechat.exception.ResponseStatus.INVALID_PASSWORD;
import static com.reactivechat.exception.ResponseStatus.INVALID_USERNAME;
import static com.reactivechat.model.session.ChatSession.Status.AUTHENTICATED;
import static com.reactivechat.model.session.ChatSession.Type.AUTHENTICATE;
import static com.reactivechat.model.session.ChatSession.Type.REAUTHENTICATE;

@Service
public class AuthenticationControllerImpl implements AuthenticationController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationControllerImpl.class);
    private static final String DEFAULT_DESCRIPTION = "Hi, I'm using SocialChat!";
    
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final AvatarController avatarController;
    private final ChatMessageController chatMessageController;
    private final MessageBroadcasterController broadcasterController;
    private final ServerDetails serverDetails;
    
    @Autowired
    public AuthenticationControllerImpl(final UserRepository userRepository,
                                        final SessionRepository sessionRepository,
                                        final AvatarControllerImpl avatarController,
                                        final ChatMessageController chatMessageController,
                                        final MessageBroadcasterController broadcasterController,
                                        final ServerDetails serverDetails) {
        
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.avatarController = avatarController;
        this.chatMessageController = chatMessageController;
        this.broadcasterController = broadcasterController;
        this.serverDetails = serverDetails;
    }
    
    @Override
    public void handleAuthenticate(final AuthenticateRequest authenticateRequest,
                                   final ChatSession chatSession) {
    
        try {
    
            AuthenticateResponse response = authenticate(authenticateRequest, chatSession);
    
            final ResponseMessage<Object> responseMessage = ResponseMessage
                .builder()
                .type(MessageType.AUTHENTICATE)
                .payload(response)
                .build();
    
            broadcasterController.broadcastToSession(chatSession, responseMessage);
            
        } catch (ChatException e) {
    
            LOGGER.error("Failed to authenticate user {}. Reason: {}", authenticateRequest.getUsername(), e.getMessage());
            
            final ResponseMessage<Object> responseMessage = ResponseMessage
                .builder()
                .type(MessageType.AUTHENTICATE)
                .payload(e.toErrorMessage())
                .build();
    
            broadcasterController.broadcastToSession(chatSession, responseMessage);
            
        }

    }
    
    @Override
    public void handleReauthenticate(final ReauthenticateRequest reauthenticateRequest, final ChatSession chatSession) {
    
        try {
    
            final ChatSession newSession = chatSession.from()
                .id(UUID.randomUUID().toString())
                .serverDetails(serverDetails)
                .startDate(OffsetDateTime.now().toString())
                .status(AUTHENTICATED)
                .type(REAUTHENTICATE)
                .build();
            
            final String userId = sessionRepository.reauthenticate(newSession, reauthenticateRequest.getToken())
                .block();
    
            final User user = userRepository.findById(userId)
                .blockOptional()
                .orElseThrow(() -> new ChatException("Couldn't identify user for token", INVALID_CREDENTIALS));
    
            final AuthenticateResponse authenticateResponse = AuthenticateResponse.builder()
                .user(mapToUserDTO(user))
                .token(reauthenticateRequest.getToken())
                .status(ResponseStatus.SUCCESS)
                .build();
    
            final ResponseMessage<Object> responseMessage = ResponseMessage
                .builder()
                .type(MessageType.REAUTHENTICATE)
                .payload(authenticateResponse)
                .build();
        
            broadcasterController.broadcastToSession(chatSession, responseMessage);
    
            LOGGER.info("Session {} reauthenticated with token {} of user {}",
                newSession.getId(),
                reauthenticateRequest.getToken(),
                user.getUsername()
            );
            
        } catch (ChatException e) {
        
            LOGGER.error("Failed to reauthenticate with token {}. Reason: {}",
                reauthenticateRequest.getToken(), e.getMessage()
            );
            
            final ResponseMessage<Object> responseMessage = ResponseMessage
                .builder()
                .type(MessageType.NOT_AUTHENTICATED)
                .payload(e.toErrorMessage())
                .build();
        
            broadcasterController.broadcastToSession(chatSession, responseMessage);
        
        }
        
    }
    
    @Override
    public void handleSignup(final SignupRequest signupRequest, final ChatSession chatSession) {
    
        try {
    
            validateSignUpRequest(signupRequest);
            
            final Mono<User> createdUser = userRepository.create(mapToUser(signupRequest));
    
            AuthenticateRequest authenticateRequest = AuthenticateRequest.builder()
                .username(signupRequest.getUsername())
                .password(signupRequest.getPassword())
                .build();
    
            AuthenticateResponse authenticateResponse = authenticate(authenticateRequest, chatSession);
    
            final ResponseMessage<Object> responseMessage = ResponseMessage
                .builder()
                .type(MessageType.SIGNUP)
                .payload(authenticateResponse)
                .build();
    
            broadcasterController.broadcastToSession(chatSession, responseMessage);
            chatMessageController.handleNewContact(createdUser.block(), chatSession);
    
            LOGGER.info("New user registered: {}", signupRequest.getUsername());
            
        } catch (ChatException e) {
    
            LOGGER.error("Failed to create user {}. Reason: {}", signupRequest.getUsername(), e.getMessage());
    
            final ResponseMessage<Object> responseMessage = ResponseMessage
                .builder()
                .type(MessageType.SIGNUP)
                .payload(e.toErrorMessage())
                .build();
    
            broadcasterController.broadcastToSession(chatSession, responseMessage);
            
        }
        
    }

    @Override
    public void logoff(final ChatSession chatSession) {
        sessionRepository.logoff(chatSession);
    }
    
    private AuthenticateResponse authenticate(final AuthenticateRequest authenticateRequest, final ChatSession chatSession) {
        
        Optional<User> userOpt = userRepository.findFullDetailsByUsername(authenticateRequest.getUsername())
            .blockOptional();
        
        if (userOpt.isPresent() && userOpt.get().getPassword().equals(authenticateRequest.getPassword())) {
            
            try {
    
                final ChatSession newSession = chatSession.from()
                    .id(UUID.randomUUID().toString())
                    .serverDetails(serverDetails)
                    .userDeviceDetails(authenticateRequest.getUserDeviceDetails())
                    .startDate(OffsetDateTime.now().toString())
                    .status(AUTHENTICATED)
                    .type(AUTHENTICATE)
                    .build();
    
                final User user = userOpt.get();
                final String token = buildToken(newSession, user);
                sessionRepository.authenticate(newSession, user, token);
                
                LOGGER.info("New session authenticated: {}", newSession.getId());
                
                return AuthenticateResponse.builder()
                    .user(mapToUserDTO(user))
                    .token(token)
                    .status(ResponseStatus.SUCCESS)
                    .build();
                
            } catch (Exception e) {
                throw new ChatException("Failed to authenticate. Reason: " + e.getMessage(), ResponseStatus.SERVER_ERROR);
            }
            
        }
        
        throw new ChatException("Invalid Credentials", INVALID_CREDENTIALS);
    }
    
    private String buildToken(final ChatSession session, final User user) {
        
        final String token = UUID.randomUUID().toString() + "-" + user.getId() + "-" + session.getId();
        
        return Base64
            .getEncoder()
            .encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }
    
    private void validateSignUpRequest(final SignupRequest signupRequest) {
        
        if (signupRequest.getName() == null || signupRequest.getName().trim().isEmpty()) {
            throw new ChatException("Name must be defined", INVALID_NAME);
        }
        
        if (signupRequest.getUsername() == null || signupRequest.getUsername().trim().isEmpty()) {
            throw new ChatException("Username must be defined", INVALID_USERNAME);
        }
        
        if (signupRequest.getPassword() == null || signupRequest.getPassword().trim().isEmpty()) {
            throw new ChatException("Username must be defined", INVALID_PASSWORD);
        }
        
    }
    
    private User mapToUser(final SignupRequest signupRequest) {
        return User.builder()
            .username(signupRequest.getUsername())
            .password(signupRequest.getPassword())
            .name(signupRequest.getName())
            .avatar(avatarController.pickRandomAvatar())
            .description(DEFAULT_DESCRIPTION)
            .build();
    }
    
    private UserDTO mapToUserDTO(final User user) {
        return UserDTO.builder()
            .id(user.getId())
            .name(user.getName())
            .description(user.getDescription())
            .avatar(user.getAvatar())
            .build();
    }
    
}
