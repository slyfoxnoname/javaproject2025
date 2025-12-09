package com.webbot.websercher;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.webbot.model.ScrapeResult;

public class WebScraper {

    private static final String SAVE_DIR = "src/Lib/";
    private static final String LOG_DIR = "src/log/";
    
    // Маскування під звичайний браузер (щоб сайти швидше відповідали)
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";

    // --- ПОШУК (DuckDuckGo) ---
    public List<String[]> findSitesByKeyword(String query) {
        List<String[]> foundSites = new ArrayList<>();
        try {
            String url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(5000) // Швидкий таймаут (5 сек)
                    .get();
            
            Elements results = doc.select("a.result__a");
            int limit = 0;
            for (Element res : results) {
                if (limit >= 5) break;
                String title = res.text();
                String link = res.attr("href");
                if (link != null && !link.isEmpty()) {
                    foundSites.add(new String[]{title, link});
                    limit++;
                }
            }
        } catch (IOException e) {
            System.err.println("Search error: " + e.getMessage());
        }
        return foundSites;
    }

    // --- СКАНУВАННЯ (ВИПРАВЛЕНО ТЕКСТ) ---
    public ScrapeResult processSite(String url, String targetTag, String keywordFilter) {
        ScrapeResult result = new ScrapeResult(url);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(10000) // 10 секунд на завантаження сайту
                    .get();

            if (doc.title() != null && !doc.title().isEmpty()) {
                result.setTitle(doc.title());
            }

            // Якщо теги не вказані, беремо основні текстові блоки
            String tagsToSearch = (targetTag == null || targetTag.isEmpty()) ? "h1, h2, p, article, div.content, div.text" : targetTag;
            
            Elements infoElements = doc.select(tagsToSearch);
            for (Element el : infoElements) {
                // Використовуємо text(), а не ownText(), щоб брати весь зміст
                String text = el.text().trim();
                
                // Фільтр: ігноруємо зовсім коротке сміття (менше 5 символів), але беремо решту
                if (text.length() > 5) {
                    if (keywordFilter == null || text.toLowerCase().contains(keywordFilter.toLowerCase())) {
                        result.addInfo(text);
                    }
                }
            }

            // Медіа
            Elements media = doc.select("img[src], a[href]");
            for (Element src : media) {
                String link = src.is("img") ? src.attr("abs:src") : src.attr("abs:href");
                if (link.endsWith(".jpg") || link.endsWith(".png") || link.endsWith(".pdf")) {
                    result.addMediaUrl(link);
                }
            }
            result.setStatus("SUCCESS");
        } catch (IOException e) {
            result.setStatus("ERROR: " + e.getMessage());
        }
        return result;
    }
    
    public ScrapeResult processSite(String url, String targetTag) {
        return processSite(url, targetTag, null);
    }

    // --- ЗАВАНТАЖЕННЯ ---
    public boolean performDownload(String fileUrl) {
        try {
            URL url = new URL(fileUrl);
            String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            fileName = fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
            if (fileName.length() > 50) fileName = fileName.substring(fileName.length() - 50);

            if (Files.exists(Paths.get(SAVE_DIR + fileName))) return false; 
            Files.createDirectories(Paths.get(SAVE_DIR));
            
            try (BufferedInputStream in = new BufferedInputStream(url.openStream());
                 FileOutputStream fileOutputStream = new FileOutputStream(SAVE_DIR + fileName)) {
                byte[] dataBuffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(dataBuffer, 0, 4096)) != -1) {
                    fileOutputStream.write(dataBuffer, 0, bytesRead);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public int downloadFiles(List<String> urls) {
        int count = 0;
        for (String link : urls) {
            if (performDownload(link)) count++;
        }
        return count;
    }

    // --- ЗВІТИ ---
    public String saveTextToFile(String title, List<String> textLines) {
        try {
            String safeName = title.replaceAll("[^a-zA-Z0-9а-яА-Я ._-]", "").trim();
            if (safeName.isEmpty()) safeName = "Unknown_Site";
            if (safeName.length() > 50) safeName = safeName.substring(0, 50);
            String fileName = safeName + ".txt";
            Files.createDirectories(Paths.get(SAVE_DIR));
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(SAVE_DIR + fileName))) {
                writer.write("SOURCE: " + title); writer.newLine();
                writer.write("====================================="); writer.newLine();
                for (String line : textLines) { writer.write(line); writer.newLine(); writer.newLine(); }
            }
            return fileName;
        } catch (IOException e) { return null; }
    }

    public String saveGeneralReport(List<ScrapeResult> results) {
        String fileName = "Zvit_" + System.currentTimeMillis() + ".txt";
        try {
            Files.createDirectories(Paths.get(LOG_DIR));
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_DIR + fileName))) {
                writer.write("=== ZVIT (LOG) ==="); writer.newLine();
                writer.write("Time: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))); writer.newLine();
                writer.write("Sites: " + results.size()); writer.newLine();
                writer.write("============================"); writer.newLine();
                for (ScrapeResult res : results) {
                    writer.write("URL: " + res.getUrl()); writer.newLine();
                    writer.write("Info: " + res.getTitle()); writer.newLine();
                    writer.write("Status: " + res.getStatus()); writer.newLine();
                    if ("SUCCESS".equals(res.getStatus())) {
                        writer.write("  + Media: " + res.getMediaUrls().size()); writer.newLine();
                        writer.write("  + Text Blocks: " + res.getFoundInfo().size());
                    } else { writer.write("  ! ERROR"); }
                    writer.newLine(); writer.write("----------------------------"); writer.newLine();
                }
            }
            return fileName;
        } catch (IOException e) { return null; }
    }
}