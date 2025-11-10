package com.site.xidong.siteUser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SiteUserRepository extends JpaRepository<SiteUser, Long> {

    Optional<SiteUser> findSiteUserByEmail(String email);

    Optional<SiteUser> findSiteUserByUsername(String username);

    @Query("SELECT s FROM SiteUser s WHERE s.username = :username and s.loginMethod = :loginMethod")
    Optional<SiteUser> findByUsernameAndLoginMethod(String username, LoginMethod loginMethod);
}
