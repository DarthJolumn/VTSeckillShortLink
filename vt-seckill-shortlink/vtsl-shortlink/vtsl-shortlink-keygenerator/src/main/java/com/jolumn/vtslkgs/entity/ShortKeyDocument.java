package com.jolumn.vtslkgs.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "shortkeys")
public class ShortKeyDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String key;

    private String status;

    @Field("createdAt")
    private Instant createdAt;

    public ShortKeyDocument() {}

    public ShortKeyDocument(String key, String status, Instant createdAt) {
        this.key = key;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
