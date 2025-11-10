package com.site.xidong.utils;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnailator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class LocalUploader {
    @Value("${com.site.xidong.upload.path}")
    private String uploadPath;

    public List<String> uploadLocal(MultipartFile multipartFile) {

        if (multipartFile == null || multipartFile.isEmpty()) {
            return null;
        }

        String uuid = UUID.randomUUID().toString();
        String saveFileName = uuid + "_" + multipartFile.getOriginalFilename();

        Path savePath = Paths.get(uploadPath, saveFileName);

        List<String> savePathList = new ArrayList<>();

        try {
            multipartFile.transferTo(savePath);

            savePathList.add(savePath.toFile().getAbsolutePath());

            log.info("file type: " + Files.probeContentType(savePath));

            if (Files.probeContentType(savePath).startsWith("image")) {
                File thumbFile = new File(uploadPath, "s_" + saveFileName);
                savePathList.add(thumbFile.getAbsolutePath());
                Thumbnailator.createThumbnail(savePath.toFile(), thumbFile, 200, 200);
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
        return savePathList;
    }
}
