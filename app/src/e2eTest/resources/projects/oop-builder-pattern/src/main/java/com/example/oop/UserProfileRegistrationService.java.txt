package com.example.oop;

public class UserProfileRegistrationService {

    public UserProfile register(String userId, String displayName, String email, boolean subscribe) {
        return UserProfile.builder(userId)
                .displayName(displayName)
                .email(email)
                .newsletterSubscribed(subscribe)
                .build();
    }
}
