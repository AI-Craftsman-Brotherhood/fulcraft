package com.example.oop;

public final class UserProfile {

    private final String userId;
    private final String displayName;
    private final String email;
    private final boolean newsletterSubscribed;

    private UserProfile(Builder builder) {
        this.userId = builder.userId;
        this.displayName = builder.displayName;
        this.email = builder.email;
        this.newsletterSubscribed = builder.newsletterSubscribed;
    }

    public static Builder builder(String userId) {
        return new Builder(userId);
    }

    public String userId() {
        return userId;
    }

    public String displayName() {
        return displayName;
    }

    public String email() {
        return email;
    }

    public boolean newsletterSubscribed() {
        return newsletterSubscribed;
    }

    public static final class Builder {

        private final String userId;
        private String displayName = "Guest";
        private String email = "";
        private boolean newsletterSubscribed;

        private Builder(String userId) {
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("userId must not be blank");
            }
            this.userId = userId;
        }

        public Builder displayName(String displayName) {
            if (displayName != null && !displayName.isBlank()) {
                this.displayName = displayName;
            }
            return this;
        }

        public Builder email(String email) {
            if (email != null) {
                this.email = email;
            }
            return this;
        }

        public Builder newsletterSubscribed(boolean subscribed) {
            this.newsletterSubscribed = subscribed;
            return this;
        }

        public UserProfile build() {
            return new UserProfile(this);
        }
    }
}
