package com.StockLab.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Firebase Admin SDK 초기화 설정
 */
@Configuration
@Slf4j
public class FirebaseConfig {

    // application.yml에서 firebase.config-path 값을 주입받음
    @Value("${firebase.config-path}")
    private String firebaseConfigPath;

    /**
     * FirebaseApp 초기화 (한 번만 실행)
     */
    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        log.info("Initializing Firebase Admin SDK...");

        FileInputStream serviceAccount = new FileInputStream(firebaseConfigPath);

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

        FirebaseApp app;
        if (FirebaseApp.getApps().isEmpty()) {
            app = FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK successfully initialized!");
        } else {
            app = FirebaseApp.getInstance();
            log.info("Firebase Admin SDK already initialized. Using existing instance.");
        }

        return app;
    }

    /**
     * FirebaseAuth Bean 등록
     */
    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        log.info("FirebaseAuth instance created successfully!");
        return FirebaseAuth.getInstance(firebaseApp);
    }
}
