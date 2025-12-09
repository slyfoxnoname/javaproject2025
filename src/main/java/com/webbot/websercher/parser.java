package com.webbot.websercher;

import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class parser {

    public static void main(String[] args) {
        // URL веб-сайту для парсингу
        String url = "https://uk.wikipedia.org/wiki/%D0%9F%D0%BE%D1%87%D0%B0%D1%82%D0%BA%D0%BE%D0%B2%D0%B8%D0%B9_%D0%BA%D0%BE%D0%B4";

        try {
            // Завантаження HTML сторінки
            Document doc = Jsoup.connect(url).get();

            // Виводимо заголовок сторінки
            System.out.println("Title: " + doc.title());

            // Вибір всіх посилань <a>
            Elements links = doc.select("a[href]");
            System.out.println("Found links: " + links.size());

            for (Element link : links) {
                System.out.println("Text: " + link.text());
                System.out.println("Href: " + link.attr("abs:href"));
            }

            // Приклад: отримати всі заголовки h1
            Elements headers = doc.select("h1");
            for (Element h : headers) {
                System.out.println("H1: " + h.text());
            }

        } catch (IOException e) {
            System.err.println("Error fetching the URL: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
