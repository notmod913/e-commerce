package com.example.ecommerce.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    @Bean
    public Firestore getFirestore() throws Exception {
        // Read the Base64 encoded service account JSON from env variable
        String base64ServiceAccount = System.getenv("FIREBASE_SERVICE_ACCOUNT_BASE64");
        if (base64ServiceAccount == null) {
            throw new IllegalArgumentException("Environment variable FIREBASE_SERVICE_ACCOUNT_BASE64 is not set");
        }

        byte[] decodedBytes = Base64.getDecoder().decode(base64ServiceAccount);
        InputStream serviceAccountStream = new ByteArrayInputStream(decodedBytes);

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }

        return FirestoreClient.getFirestore();
    }
}


