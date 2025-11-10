package com.site.xidong.security;

import com.site.xidong.siteUser.LoginMethod;
import jakarta.validation.constraints.Email;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SiteUserJoinDTO {
    private String username;
    private String email;
    private String password;
    private String nickname;
    private String imageUrl;
}
