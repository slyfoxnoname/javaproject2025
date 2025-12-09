package com.webbot.model;

import java.util.ArrayList;
import java.util.List;

public class ScrapeResult {
    private String url;
    private String title; // Назва сайту (<title>)
    private String status;
    private List<String> mediaUrls; // Тільки посилання, не файли
    private List<String> foundInfo;

    public ScrapeResult(String url) {
        this.url = url;
        this.mediaUrls = new ArrayList<>();
        this.foundInfo = new ArrayList<>();
        this.title = "Невідома назва";
    }

    public void setStatus(String status) { this.status = status; }
    public String getStatus() { return status; }
    
    public void setTitle(String title) { this.title = title; }
    public String getTitle() { return title; }

    public String getUrl() { return url; }
    public List<String> getMediaUrls() { return mediaUrls; }
    public List<String> getFoundInfo() { return foundInfo; }
    
    public void addMediaUrl(String url) { this.mediaUrls.add(url); }
    public void addInfo(String info) { this.foundInfo.add(info); }
}