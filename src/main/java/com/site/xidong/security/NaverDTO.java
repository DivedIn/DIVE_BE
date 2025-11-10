package com.site.xidong.security;

import lombok.Getter;
import lombok.Setter;

public class NaverDTO {
    @Getter
    @Setter
    public static class OAuthToken {
        private String access_token;
        private String refresh_token;
        private String token_type;
        private int expires_in;
        private String error;
        private String error_description;
    }

    @Getter
    public static class NaverProfile {
        private String resultcode;
        private String message;
        private Response response;

        @Getter
        public class Response {
            private String id;
            private String nickname;
            private String email;
            private String profile_image;
        }
    }
}
