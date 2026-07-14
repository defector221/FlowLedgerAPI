package com.flowledger.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowledger.notifications")
public class NotificationProperties {
    private final Email email = new Email();
    private final Whatsapp whatsapp = new Whatsapp();

    public Email getEmail() {
        return email;
    }

    public Whatsapp getWhatsapp() {
        return whatsapp;
    }

    public static class Email {
        private boolean enabled;
        private String fromEmail = "noreply@flowledger.local";
        private String fromName = "FlowLedger";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFromEmail() {
            return fromEmail;
        }

        public void setFromEmail(String fromEmail) {
            this.fromEmail = fromEmail;
        }

        public String getFromName() {
            return fromName;
        }

        public void setFromName(String fromName) {
            this.fromName = fromName;
        }
    }

    public static class Whatsapp {
        private boolean enabled;
        private String provider = "mock";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }
}
