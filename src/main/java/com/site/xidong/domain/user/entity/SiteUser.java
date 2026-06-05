package com.site.xidong.domain.user.entity;

import com.site.xidong.global.response.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteUser extends BaseTimeEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, length = 100, unique = true)
    private String username;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false)
    private String nickname;

    private String imageUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> roles = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoginMethod loginMethod;

    private String refreshToken;

    private long tokenValidTime;

    private LocalDateTime tokenIssueAt;

    @Builder
    public SiteUser(String username, String email, String password, String nickname, String imageUrl, LoginMethod loginMethod) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.imageUrl = imageUrl;
        this.loginMethod = loginMethod;
    }

    public void addRole(String role) {
        this.roles.add(role);
    }

    public void updateToken(String refreshToken, long tokenValidTime) {
        this.refreshToken = refreshToken;
        this.tokenIssueAt = LocalDateTime.now();
        this.tokenValidTime = tokenValidTime;
    }

    public void clearToken() {
        this.refreshToken = null;
        this.tokenIssueAt = null;
        this.tokenValidTime = 0L;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
