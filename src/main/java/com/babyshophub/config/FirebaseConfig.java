package com.babyshophub.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Initialises the Firebase Admin SDK once at startup.
 *
 * The service-account path is resolved in this priority order:
 *
 * 1. A filesystem path (absolute or relative, e.g.
 * ./firebase-service-account.json)
 * → read with FileInputStream; relative paths are resolved from the
 * JVM working directory (the backend root when running with mvn/spring-boot)
 * 2. A classpath resource (e.g. classpath:firebase-service-account.json)
 * → file must be inside src/main/resources/
 * 3. FIREBASE_SERVICE_ACCOUNT env var containing the raw JSON blob
 * → useful for production cloud deployments (Heroku, Render, Cloud Run)
 *
 * Set the path in .env:
 * FIREBASE_SERVICE_ACCOUNT=./firebase-service-account.json
 * Or in application.yml:
 * app.firebase.service-account-path: ./firebase-service-account.json
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    /**
     * Value comes from application.yml:
     * app.firebase.service-account-path: ${FIREBASE_SERVICE_ACCOUNT:}
     */
    @Value("${app.firebase.service-account-path:}")
    private String serviceAccountPath;

    @Value("${app.firebase.enabled:true}")
    private boolean firebaseEnabled;

    @PostConstruct
    public void init() {
        if (!firebaseEnabled) {
            log.info("[Firebase] Disabled via app.firebase.enabled=false — skipping init");
            return;
        }

        // Skip if already initialised (hot-reload safety)
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("[Firebase] Already initialised — skipping");
            return;
        }

        if (!StringUtils.hasText(serviceAccountPath)) {
            log.warn("[Firebase] app.firebase.service-account-path is not set. " +
                    "Firebase login will be unavailable.");
            return;
        }

        try {
            InputStream credentials = resolveCredentials(serviceAccountPath.trim());
            if (credentials == null) {
                log.warn("[Firebase] Could not locate service-account file at: '{}'. " +
                        "Firebase login will be unavailable.", serviceAccountPath);
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("[Firebase] Admin SDK initialised successfully from: {}", serviceAccountPath);

        } catch (IOException e) {
            log.error("[Firebase] Failed to initialise Admin SDK: {}", e.getMessage(), e);
        }
    }

    /**
     * Resolves a credential stream from the given path string:
     * - "classpath:..." → ClassPathResource
     * - everything else → treated as a file system path (absolute or relative)
     */
    private InputStream resolveCredentials(String path) throws IOException {
        if (path.startsWith("classpath:")) {
            String classpathLocation = path.substring("classpath:".length());
            ClassPathResource resource = new ClassPathResource(classpathLocation);
            if (!resource.exists()) {
                log.warn("[Firebase] Classpath resource not found: {}", classpathLocation);
                return null;
            }
            log.debug("[Firebase] Loading credentials from classpath: {}", classpathLocation);
            return resource.getInputStream();
        }

        // File system path — relative paths resolve from the JVM working directory
        // which is the backend root folder when launched with mvn spring-boot:run
        File file = new File(path);
        if (!file.exists()) {
            // Try resolving relative to the project root one level up
            file = new File(System.getProperty("user.dir"), path);
        }
        if (!file.exists() || !file.isFile()) {
            log.warn("[Firebase] Service-account file not found at: {} (resolved: {})",
                    path, file.getAbsolutePath());
            return null;
        }
        log.debug("[Firebase] Loading credentials from file: {}", file.getAbsolutePath());
        return new FileInputStream(file);
    }
}
