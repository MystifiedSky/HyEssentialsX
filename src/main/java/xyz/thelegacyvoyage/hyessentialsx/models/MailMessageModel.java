package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nullable;

public final class MailMessageModel {

    private int id;
    private String senderName;
    private String senderUuid;
    private String recipientName;
    private String recipientUuid;
    private String message;
    private long sentAt;
    private boolean read;
    private long readAt;

    @SuppressWarnings("unused")
    public MailMessageModel() {}

    public MailMessageModel(int id,
                            @Nullable String senderName,
                            @Nullable String senderUuid,
                            @Nullable String recipientName,
                            @Nullable String recipientUuid,
                            @Nullable String message,
                            long sentAt,
                            boolean read,
                            long readAt) {
        this.id = id;
        this.senderName = senderName;
        this.senderUuid = senderUuid;
        this.recipientName = recipientName;
        this.recipientUuid = recipientUuid;
        this.message = message;
        this.sentAt = sentAt;
        this.read = read;
        this.readAt = readAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Nullable
    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(@Nullable String senderName) {
        this.senderName = senderName;
    }

    @Nullable
    public String getSenderUuid() {
        return senderUuid;
    }

    public void setSenderUuid(@Nullable String senderUuid) {
        this.senderUuid = senderUuid;
    }

    @Nullable
    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(@Nullable String recipientName) {
        this.recipientName = recipientName;
    }

    @Nullable
    public String getRecipientUuid() {
        return recipientUuid;
    }

    public void setRecipientUuid(@Nullable String recipientUuid) {
        this.recipientUuid = recipientUuid;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    public void setMessage(@Nullable String message) {
        this.message = message;
    }

    public long getSentAt() {
        return sentAt;
    }

    public void setSentAt(long sentAt) {
        this.sentAt = sentAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public long getReadAt() {
        return readAt;
    }

    public void setReadAt(long readAt) {
        this.readAt = readAt;
    }
}
