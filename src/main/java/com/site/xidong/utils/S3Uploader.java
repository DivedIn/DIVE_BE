package com.site.xidong.utils;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.apache.commons.io.FileUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3Uploader {
    private final AmazonS3Client amazonS3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.cloudfront.domain}")
    private String cloudFrontDomain;

    public Map<String, String> generatePresignedUrl(String fileName) {
        // 5분 후 만료되도록 설정
        Date expiration = new Date();
        expiration.setTime(expiration.getTime() + (5 * 60 * 1000));

        // fileName을 webm 확장자로 수정
        String webmFileName = fileName.replace(".mp4", ".webm");

        // presigned URL 생성
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, webmFileName)
                .withMethod(HttpMethod.PUT)
                .withExpiration(expiration);

        // Content-Type을 WebM으로 설정
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("video/webm");
        request.setContentType("video/webm");

        URL url = amazonS3Client.generatePresignedUrl(request);

        Map<String, String> response = new HashMap<>();
        response.put("presignedUrl", url.toString());
        response.put("videoUrl", getCloudFrontUrl() + "/" + webmFileName);
        response.put("videoKey", webmFileName);
        return response;
    }

    public String uploadThumbnail(String videoUrl, String thumbnailName) {
        try {
            // WebM 비디오에서도 썸네일 추출이 가능하도록 수정
            URL url = new URL(videoUrl);
            File tempFile = File.createTempFile("temp", ".webm");
            FileUtils.copyURLToFile(url, tempFile);

            Picture picture = FrameGrab.getFrameFromFile(tempFile, 0);
            BufferedImage bufferedImage = AWTUtil.toBufferedImage(picture);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "JPEG", baos);
            byte[] bytes = baos.toByteArray();

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType("image/jpeg");

            amazonS3Client.putObject(
                    bucket,
                    thumbnailName,
                    new ByteArrayInputStream(bytes),
                    metadata
            );

            tempFile.delete();
            baos.close();

            return getCloudFrontUrl() + "/" + thumbnailName;
        } catch (Exception e) {
            log.error("Error creating thumbnail", e);
            throw new RuntimeException("Failed to create thumbnail", e);
        }
    }

    public String getCloudFrontUrl() {
        return cloudFrontDomain;
    }
}