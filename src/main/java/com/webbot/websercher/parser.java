package com.webbot.websercher;

import java.io.IOException;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.webbot.model.ScanResult;

public class parser{

    public ScanResult parseUrl(String url, String keyword) {
        ScanResult result = new ScanResult(url);

        try {
            // Імітуємо браузер
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000) // 10 секунд на з'єднання
                    .get();

            result.setStatus("OK");

            // 1. Пошук за ключовим словом (якщо задано)
            if (keyword != null && !keyword.isEmpty()) {
                Elements paragraphs = doc.getElementsContainingOwnText(keyword);
                for (Element p : paragraphs) {
                    result.addInfo(p.text().substring(0, Math.min(p.text().length(), 100)) + "...");
                }
            }

            // 2. Пошук документів (PDF, DOC)
            Elements docs = doc.select("a[href$=.pdf], a[href$=.doc], a[href$=.docx]");
            for (Element link : docs) {
                result.addDoc(link.attr("abs:href"));
            }

            // 3. Пошук медіа (Зображення)
            Elements images = doc.select("img[src]");
            for (Element img : images) {
                String src = img.attr("abs:src");
                // Фільтруємо дрібні іконки
                if (src.endsWith(".jpg") || src.endsWith(".png")) {
                    result.addMedia(src);
                }
            }

        } catch (HttpStatusException e) {
            // Обробка помилок (404, 403 і т.д.)
            result.setStatus("ERROR: Код " + e.getStatusCode());
        } catch (IOException e) {
            result.setStatus("ERROR: Недоступний (" + e.getMessage() + ")");
        } catch (Exception e) {
            result.setStatus("ERROR: Невідома помилка");
        }

        return result;
    }
    
    // Метод "Пошук в Google" (спрощений, бо Google блокує ботів)
    public ScanResult searchAndParse(String query) {
        // Для реального пошуку краще використовувати Google Custom Search API
        String searchUrl = "https://www.google.com/search?q=" + query;
        return parseUrl(searchUrl, query);
    }
}