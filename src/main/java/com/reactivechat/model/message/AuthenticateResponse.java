package com.reactivechat.model.message;

import com.reactivechat.exception.ResponseStatus;
import com.reactivechat.model.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class AuthenticateResponse {
    
    private final String token;
    private final UserDTO user;
    private final ResponseStatus status;
    
}
