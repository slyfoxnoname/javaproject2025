package com.webbot.model;

import java.util.ArrayList;
import java.util.List;

public class ScanResult {
    private String url;
    private String status; // "OK", "ERROR", "PROTECTED"
    private List<String> newInfo = new ArrayList<>(); // Текстова інформація
    private List<String> mediaLinks = new ArrayList<>(); // Зображення, відео
    private List<String> docLinks = new ArrayList<>(); // PDF, DOCX

    public ScanResult(String url) {
        this.url = url;
    }

    // Getters, Setters та методи add...
    public void addInfo(String info) { newInfo.add(info); }
    public void addMedia(String link) { mediaLinks.add(link); }
    public void addDoc(String link) { docLinks.add(link); }
    public void setStatus(String status) { this.status = status; }
    
    // Метод для формування звіту текстом
    public String toStringReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("🌐 <b>Сайт:</b> ").append(url).append("\n");
        sb.append("📊 <b>Статус:</b> ").append(status).append("\n");
        
        if (!newInfo.isEmpty()) sb.append("📝 <b>Знайдено інфо:</b> ").append(newInfo.size()).append("\n");
        if (!mediaLinks.isEmpty()) sb.append("🖼 <b>Медіа:</b> ").append(mediaLinks.size()).append("\n");
        if (!docLinks.isEmpty()) sb.append("cw <b>Документи:</b> ").append(docLinks.size()).append("\n");
        
        return sb.toString();
    }
    
    public List<String> getDocLinks() { return docLinks; }
    public List<String> getMediaLinks() { return mediaLinks; }
}