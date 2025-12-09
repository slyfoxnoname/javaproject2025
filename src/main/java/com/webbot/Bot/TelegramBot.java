package com.webbot.Bot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.webbot.model.ScanResult;
import com.webbot.websercher.parser;

public class TelegramBot extends TelegramLongPollingBot {

    private final parser parserService = new parser();
    private final List<String> urlsToMonitor = new ArrayList<>();
    private Long userChatId = null; // Запам'ятовуємо, кому слати звіт
    private ScheduledExecutorService scheduler;
    private boolean isRunning = false;

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String msg = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            this.userChatId = chatId; // Запам'ятовуємо користувача

            if (msg.equals("/start")) {
                sendMsg(chatId, "Привіт! Використовуй:\n/add [url] - додати сайт\n/run - запустити сканування раз на годину\n/stop - зупинити");
            } else if (msg.startsWith("/add ")) {
                String url = msg.substring(5).trim();
                urlsToMonitor.add(url);
                sendMsg(chatId, "Додано до моніторингу: " + url);
            } else if (msg.equals("/run")) {
                startMonitoring();
                sendMsg(chatId, "Моніторинг запущено! Чекайте звітів.");
            } else if (msg.equals("/stop")) {
                stopMonitoring();
                sendMsg(chatId, "Моніторинг зупинено.");
            }
        }
    }

    // --- Логіка планувальника ---
    private void startMonitoring() {
        if (isRunning) return;
        isRunning = true;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        
        // Запускати кожні 60 хвилин (або змініть на TimeUnit.SECONDS для тесту)
        scheduler.scheduleAtFixedRate(this::performScanTask, 0, 60, TimeUnit.MINUTES);
    }

    private void stopMonitoring() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        isRunning = false;
    }

    // Цей метод викликається автоматично таймером
    private void performScanTask() {
        if (userChatId == null || urlsToMonitor.isEmpty()) return;

        StringBuilder globalReport = new StringBuilder("🔔 <b>АВТОМАТИЧНИЙ ЗВІТ</b>\n\n");
        
        for (String url : urlsToMonitor) {
            // Викликаємо парсер (наприклад, шукаємо слово "java" або просто збираємо лінки)
            ScanResult result = parserService.parseUrl(url, "java"); 
            
            globalReport.append(result.toStringReport()).append("\n----------------\n");
            
            // Якщо є документи, надсилаємо посилання окремо
            for (String doc : result.getDocLinks()) {
                sendMsg(userChatId, "Знайдено документ: " + doc);
                // Тут можна додати логіку завантаження файлу (SendDocument), якщо потрібно
            }
        }

        sendMsg(userChatId, globalReport.toString());
    }

    private void sendMsg(long chatId, String text) {
        SendMessage sm = new SendMessage();
        sm.setChatId(String.valueOf(chatId));
        sm.setText(text);
        sm.setParseMode("HTML"); // Дозволяє форматування жирним шрифтом
        try {
            execute(sm);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() { return "WebMehSercheBot"; }
    @Override
    public String getBotToken() { return "8401459661:AAFYnUidQYUM0IsTDzlkwNSyRKW-LSK81U4"; }
}