package com.site.xidong.video;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, Long> {
    Optional<Video> findById(Long id);

    @Query("SELECT v FROM Video v WHERE v.isOpen = true")
    List<Video> findAllOpenVideos();

    @Query("SELECT v FROM Video v WHERE v.siteUser.username = :username")
    List<Video> findMyVideos(String username);
}
