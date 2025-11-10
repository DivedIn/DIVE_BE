package com.site.xidong.config;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribe.TranscribeClient;

public class AWSClient {
    public static void main(String[] args) {
        TranscribeClient transcribeClient = TranscribeClient.builder()
                .region(Region.AP_NORTHEAST_2)  // 서울 리전
                .credentialsProvider(DefaultCredentialsProvider.create()) // IAM 역할 자동 인식
                .build();

        System.out.println("AWS Transcribe Client가 성공적으로 생성되었습니다.");
    }
}
