package com.site.xidong.domain.user.controller;

import com.site.xidong.domain.user.dto.SiteUserDTO;
import com.site.xidong.domain.user.dto.SiteUserJoinDTO;
import com.site.xidong.domain.user.dto.SiteUserLoginDTO;
import com.site.xidong.domain.user.dto.Token;
import com.site.xidong.domain.user.service.SiteUserService;
import com.site.xidong.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/siteUser")
@Slf4j
public class SiteUserController {
    private final SiteUserService siteUserService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Token>> join(@RequestBody SiteUserJoinDTO siteUserJoinDTO) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(siteUserService.join(siteUserJoinDTO)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Token>> login(@RequestBody SiteUserLoginDTO siteUserLoginDTO) {
        return ResponseEntity.ok(ApiResponse.success(siteUserService.login(siteUserLoginDTO)));
    }

    @GetMapping("/myInfo")
    public ResponseEntity<ApiResponse<SiteUserDTO>> getMyInfo(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(ApiResponse.success(siteUserService.getMyInfo(ud.getUsername())));
    }
}
