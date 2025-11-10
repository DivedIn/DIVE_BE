package com.site.xidong.siteUser;

import com.site.xidong.security.SiteUserJoinDTO;
import com.site.xidong.security.SiteUserLoginDTO;
import com.site.xidong.security.Token;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/siteUser")
@Slf4j
public class SiteUserController {
    private final SiteUserService siteUserService;

    @PostMapping("/signup")
    public ResponseEntity<Token> join(@RequestBody SiteUserJoinDTO siteUserJoinDTO) throws Exception {
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
