package com.site.xidong.domain.user.service;

import com.site.xidong.domain.user.entity.SiteUser;
import com.site.xidong.domain.user.repository.SiteUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import import com.site.xidong.domain.user.dto.SiteUserSecurityDTO;;

@Log4j2
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final SiteUserRepository siteUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<SiteUser> result = siteUserRepository.findSiteUserByUsername(username);
        if(result.isEmpty()) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
        }
        SiteUser siteUser = result.get();
        SiteUserSecurityDTO siteUserSecurityDTO = new SiteUserSecurityDTO(siteUser.getUsername(), siteUser.getPassword(), siteUser.getAuthorities());
        return siteUserSecurityDTO;
    }
}
