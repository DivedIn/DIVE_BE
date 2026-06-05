package com.site.xidong.domain.user.controller;

import com.site.xidong.domain.user.dto.SiteUserJoinDTO;
import com.site.xidong.domain.user.dto.SiteUserLoginDTO;
import com.site.xidong.domain.user.dto.Token;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import com.site.xidong.domain.user.service.SiteUserService;
import com.site.xidong.domain.user.dto.SiteUserDTO;

@RequiredArgsConstructor
@RestController
@RequestMapping("/siteUser")
@Slf4j
public class SiteUserController {
    private final SiteUserService siteUserService;

    @PostMapping("/signup")
    public ResponseEntity<Token> join(@RequestBody SiteUserJoinDTO siteUserJoinDTO) {
        Token jwtToken;
        try {
            jwtToken = siteUserService.join(siteUserJoinDTO);
        } catch(Exception e) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        return ResponseEntity.status(HttpStatus.OK).body(jwtToken);
    }

    @PostMapping("/login")
    public ResponseEntity<Token> login(@RequestBody SiteUserLoginDTO siteUserLoginDTO) throws UsernameNotFoundException, Exception {
        Token jwtToken;
        try {
            jwtToken = siteUserService.login(siteUserLoginDTO);
        } catch(UsernameNotFoundException e1) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        } catch(Exception e2) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.status(HttpStatus.OK).body(jwtToken);
    }

    @GetMapping("/myInfo")
    public ResponseEntity<SiteUserDTO> getMyInfo() {
        return ResponseEntity.status(HttpStatus.OK).body(siteUserService.getMyInfo());
    }

}
