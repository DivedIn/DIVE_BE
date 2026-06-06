package com.site.xidong.domain.video.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import javax.imageio.*;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private static final String DEFAULT_THUMBNAIL_URL =
            "https://dive-s3-ver2.s3.ap-northeast-2.amazonaws.com/Gk9C7kwWkAATlwl.jpeg";
    private static final float JPEG_QUALITY = 0.85f;
    private static final int THUMB_MAX_WIDTH = 800;
    private static final int THUMB_MAX_HEIGHT = 600;

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

    /**
     * S3에서 presigned URL로 영상을 가져와 첫 프레임을 썸네일로 생성하고 S3에 업로드한다.
     *
     * @return 업로드된 썸네일의 S3 public URL (실패 시 기본 이미지 URL)
     */
    public String generate(String videoKey) {
        String thumbnailKey = videoKey.replace(".webm", "-thumb.jpg");
        try {
            String presignedUrl = createPresignedUrl(videoKey, Duration.ofMinutes(10));
            Path tempFile = extractFirstFrame(presignedUrl, videoKey);
            if (tempFile == null) return DEFAULT_THUMBNAIL_URL;

            uploadToS3(tempFile, thumbnailKey);
            Files.delete(tempFile);
            return s3UrlOf(thumbnailKey);
        } catch (Exception e) {
            log.warn("썸네일 생성 실패, 기본 이미지 사용: {}", e.getMessage());
            return DEFAULT_THUMBNAIL_URL;
        }
    }

    private String createPresignedUrl(String videoKey, Duration duration) {
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {
            GetObjectRequest getReq = GetObjectRequest.builder().bucket(bucket).key(videoKey).build();
            PresignedGetObjectRequest presigned = presigner.presignGetObject(r -> r
                    .signatureDuration(duration)
                    .getObjectRequest(getReq));
            return presigned.url().toString();
        }
    }

    private Path extractFirstFrame(String presignedUrl, String videoKey) throws IOException {
        Path tempFile = Files.createTempFile("thumb_", ".jpg");
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(presignedUrl);
        try {
            grabber.setOption("threads", "auto");
            grabber.setOption("analyzeduration", "1000000");
            grabber.setOption("probesize", "1000000");
            grabber.start();
            grabber.setTimestamp(1000);

            Frame frame = grabber.grabImage();
            if (frame == null) frame = grabber.grabImage();
            if (frame == null) {
                log.error("프레임 추출 실패: {}", videoKey);
                return null;
            }

            Java2DFrameConverter converter = new Java2DFrameConverter();
            BufferedImage image = converter.convert(frame);
            if (image == null) {
                log.error("프레임 → 이미지 변환 실패");
                return null;
            }

            saveAsJpeg(resizeImage(image), tempFile.toFile());
            log.info("썸네일 프레임 추출 완료: {} bytes", Files.size(tempFile));
            return tempFile;
        } finally {
            try { grabber.stop(); grabber.release(); } catch (Exception e) {
                log.warn("FFmpegFrameGrabber 해제 실패: {}", e.getMessage());
            }
        }
    }

    private void uploadToS3(Path file, String key) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket).key(key)
                        .contentType("image/jpeg")
                        .acl(ObjectCannedACL.PUBLIC_READ)
                        .build(),
                RequestBody.fromFile(file)
        );
        log.info("썸네일 S3 업로드 완료: {}", key);
    }

    private BufferedImage resizeImage(BufferedImage original) {
        double scale = Math.min(
                (double) THUMB_MAX_WIDTH / original.getWidth(),
                (double) THUMB_MAX_HEIGHT / original.getHeight()
        );
        if (scale >= 1.0) return original;

        int w = (int) (original.getWidth() * scale);
        int h = (int) (original.getHeight() * scale);
        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(original, 0, 0, w, h, null);
        g.dispose();
        return resized;
    }

    private void saveAsJpeg(BufferedImage image, File output) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IllegalStateException("JPEG writer를 찾을 수 없습니다");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
        }
        try (FileImageOutputStream out = new FileImageOutputStream(output)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private String s3UrlOf(String key) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, key);
    }
}
