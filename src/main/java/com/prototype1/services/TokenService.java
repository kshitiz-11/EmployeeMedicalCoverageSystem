package com.prototype1.services;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;

import com.google.api.client.json.jackson2.JacksonFactory;
//import com.info7255.project.Exception.BadRequest;
//import org.json.simple.parser.JSONParser;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

@Service
public class TokenService {

    private static String CLIENT_ID = "732879764903-sf4tkpm4os716o51hla8t32shskji026.apps.googleusercontent.com";
    private static final HttpTransport transport = new NetHttpTransport();
    private static final JsonFactory jsonFactory = new JacksonFactory();


    public boolean validateToken(String token) throws Exception {
        if(token==null || token.isEmpty()) throw new Exception("Token not found");
        String token1 = "";
        if(!token.contains("Bearer ")) throw new Exception("Invalid token format");
        token1 = token.substring(7);
        System.out.println("token value is "+token1);
        if(!authorize(token1)) throw new Exception("Token is invalid");
        return true;
    }

    public boolean authorize(String token) throws GeneralSecurityException, IOException {


        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                // Specify the CLIENT_ID of the app that accesses the backend:
                //.setAudience(Collections.singletonList(CLIENT_ID))
                // Or, if multiple clients access the backend:
                .setAudience(Arrays.asList(CLIENT_ID))
                .build();






// (Receive idTokenString by HTTPS POST)


        GoogleIdToken idToken = verifier.verify(token);
        if (idToken != null) {
            Payload payload = idToken.getPayload();

            // Print user identifier
            String userId = payload.getSubject();
            System.out.println("User ID: " + userId);

            String issuer = idToken.getPayload().getIssuer();

            String email = payload.getEmail();

            return true;


        } else {
            System.out.println("Invalid ID token.");
            return false;
        }
    }
}
