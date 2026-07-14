package com.flowledger.notification;

import java.util.ArrayList;
import java.util.List;

public class NotificationResult {
    private final List<ChannelResult> channels = new ArrayList<>();

    public void add(ChannelResult result) {
        channels.add(result);
    }

    public List<ChannelResult> getChannels() {
        return channels;
    }

    public boolean anySent() {
        return channels.stream().anyMatch(ChannelResult::sent);
    }

    public boolean allFailed() {
        return !channels.isEmpty() && channels.stream().noneMatch(ChannelResult::sent);
    }

    public record ChannelResult(NotificationChannel channel, boolean sent, String recipient, String error) {
        public static ChannelResult ok(NotificationChannel channel, String recipient) {
            return new ChannelResult(channel, true, recipient, null);
        }

        public static ChannelResult failed(NotificationChannel channel, String recipient, String error) {
            return new ChannelResult(channel, false, recipient, error);
        }

        public static ChannelResult skipped(NotificationChannel channel, String reason) {
            return new ChannelResult(channel, false, null, reason);
        }
    }
}
