package com.webbot.Bot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import com.webbot.model.ScrapeResult;
import com.webbot.websercher.WebScraper;

public class TelegramInterface extends TelegramLongPollingBot {

    private final WebScraper scraper = new WebScraper();
    
    // ❗ ВСТАВ СЮДИ СВОЇ ДАНІ
    private final String BOT_USERNAME = "WebMehSercheBot";
    private final String BOT_TOKEN = "8401459661:AAFYnUidQYUM0IsTDzlkwNSyRKW-LSK81U4";

    private final List<String> sites = new ArrayList<>(Arrays.asList(
            "https://en.wikipedia.org/wiki/Java_(programming_language)",
            "https://habr.com/ru/all/"
    ));

    private final Map<Long, String> userStates = new HashMap<>();
    private final Map<Long, Integer> tempHelpMessages = new HashMap<>();
    private final List<ScrapeResult> lastResults = Collections.synchronizedList(new ArrayList<>());

    private final Map<Long, List<String[]>> tempSearchResults = new ConcurrentHashMap<>();
    private final Map<Long, String> tempSelectedSite = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> processingState = new ConcurrentHashMap<>();
    
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public String getBotUsername() { return BOT_USERNAME; }

    @Override
    public String getBotToken() { return BOT_TOKEN; }

    @Override
    public void onUpdateReceived(Update update) {
        Long chatId = null;
        if (update.hasMessage()) chatId = update.getMessage().getChatId();
        else if (update.hasCallbackQuery()) chatId = update.getCallbackQuery().getMessage().getChatId();

        if (chatId != null && processingState.getOrDefault(chatId, false)) {
            if (update.hasCallbackQuery()) sendAlert(update.getCallbackQuery().getId(), "⏳ Зачекайте! Я працюю...", true);
            return;
        }

        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();
            String callbackId = update.getCallbackQuery().getId();

            // ТУТ ВАЖЛИВО: Ми НЕ видаляємо повідомлення, якщо це дія "зберегти текст"
            // або інші дії, де треба залишитися на екрані.
            // Але для переходів між меню (settings, back) - видаляємо.
            
            if (!callbackData.startsWith("save_text_") && !callbackData.startsWith("btn_download") && !callbackData.startsWith("btn_save_report_file")) {
                 deleteMessage(chatId, messageId);
            }

            switch (callbackData) {
                case "btn_scan": startScanning(chatId); break;
                
                case "btn_web_search": 
                    clearTempMessage(chatId);
                    userStates.put(chatId, "WAITING_SEARCH_QUERY");
                    Message msg = sendMessageReturning(chatId, "🔎 **Введіть тему для пошуку:**\n_(Наприклад: Java lessons)_", true);
                    if (msg != null) tempHelpMessages.put(chatId, msg.getMessageId());
                    break;
                    
                case "btn_report": showGeneralReport(chatId); break;
                case "btn_save_report_file": saveReportToFile(chatId, callbackId); break;
                case "btn_download": downloadFoundMedia(chatId, callbackId); break;
                case "btn_settings": clearTempMessage(chatId); sendSettingsMenu(chatId); break;
                
                case "btn_add_site": 
                    userStates.put(chatId, "WAITING_FOR_URL");
                    Message instr = sendMessageReturning(chatId, "🔗 **Введіть посилання:**", true);
                    if (instr != null) tempHelpMessages.put(chatId, instr.getMessageId());
                    break;
                    
                case "btn_clear_sites": 
                    sites.clear(); 
                    sendSettingsMenu(chatId); 
                    sendAlert(callbackId, "🗑 Очищено!", false); 
                    break;
                    
                case "btn_back": 
                    clearTempMessage(chatId);
                    userStates.remove(chatId); 
                    sendMainMenu(chatId); 
                    break;
                    
                case "btn_back_to_report": 
                    // Тут видаляємо повідомлення з текстом і показуємо список
                    deleteMessage(chatId, messageId);
                    showScanResults(chatId); 
                    break;

                default:
                    if (callbackData.startsWith("view_text_")) {
                        // Видаляємо список, показуємо текст
                        deleteMessage(chatId, messageId);
                        viewSiteText(chatId, Integer.parseInt(callbackData.replace("view_text_", "")));
                    } else if (callbackData.startsWith("save_text_")) {
                        // Збереження тексту (БЕЗ ВИДАЛЕННЯ ВІКНА)
                        saveTextContent(chatId, Integer.parseInt(callbackData.replace("save_text_", "")), callbackId);
                    } else if (callbackData.startsWith("pick_site_")) {
                        handleSiteSelection(chatId, Integer.parseInt(callbackData.replace("pick_site_", "")));
                    }
                    break;
            }
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            int msgId = update.getMessage().getMessageId();
            
            deleteMessage(chatId, msgId);

            if (text.equals("/start")) sendMainMenu(chatId);
            else if ("WAITING_FOR_URL".equals(userStates.get(chatId))) addNewSites(chatId, text);
            else if ("WAITING_SEARCH_QUERY".equals(userStates.get(chatId))) performWebSearch(chatId, text);
            else if ("WAITING_INTERNAL_KEYWORD".equals(userStates.get(chatId))) performInternalSearch(chatId, text);
        }
    }

    // --- 🔥 ВИПРАВЛЕНО: ТІЛЬКИ АЛЕРТ, НІЯКИХ ПЕРЕХОДІВ ---
    private void saveTextContent(long chatId, int index, String callbackId) {
        if (index >= lastResults.size()) return;
        
        ScrapeResult result = lastResults.get(index);
        String fileName = scraper.saveTextToFile(result.getTitle(), result.getFoundInfo());
        
        if (fileName != null) {
            // true = показати віконце з кнопкою ОК по центру екрана
            sendAlert(callbackId, "✅ Текст успішно збережено!\nФайл: src/Lib/" + fileName, true);
        } else {
            sendAlert(callbackId, "❌ Помилка збереження файлу.", true);
        }
        // Більше нічого не робимо -> користувач залишається читати текст
    }

    // --- ІНШІ МЕТОДИ (БЕЗ ЗМІН ДИЗАЙНУ) ---

    private void startScanning(long chatId) {
        processingState.put(chatId, true);
        executor.submit(() -> {
            try {
                Message loading = sendMessageReturning(chatId, "📡 **Сканую мережу...**", false);
                lastResults.clear();
                for (String url : sites) {
                    lastResults.add(scraper.processSite(url, "h1, h2, p, article"));
                }
                if (loading != null) deleteMessage(chatId, loading.getMessageId());
                showScanResults(chatId);
            } finally {
                processingState.put(chatId, false);
            }
        });
    }

    private void sendSettingsMenu(long chatId) {
        StringBuilder sitesList = new StringBuilder();
        if (sites.isEmpty()) {
            sitesList.append("_(список порожній)_");
        } else {
            for (String s : sites) {
                sitesList.append("▫️ ").append(s).append("\n");
            }
        }

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("⚙️ **Налаштування**\n\n🎯 **Список цілей:**\n" + sitesList);
        message.setParseMode("Markdown");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(createRow(createButton("➕ Додати", "btn_add_site")));
        rows.add(createRow(createButton("🗑 Очистити", "btn_clear_sites")));
        rows.add(createRow(createButton("🔙 Назад", "btn_back")));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        executeMessageReturningMessage(message);
    }

    private void addNewSites(long chatId, String text) {
        clearTempMessage(chatId);
        String[] urls = text.split("[\\s,]+");
        int added = 0;
        int duplicates = 0;

        for (String url : urls) {
            if (url.startsWith("http")) {
                if (!sites.contains(url)) {
                    sites.add(url);
                    added++;
                } else {
                    duplicates++;
                }
            }
        }
        userStates.remove(chatId);
        
        String msg = "✅ Додано: " + added;
        if (duplicates > 0) msg += "\n🚫 Дублікатів (пропущено): " + duplicates;
        
        Message res = sendMessageReturning(chatId, msg, false);
        if (res != null) tempHelpMessages.put(chatId, res.getMessageId());
        
        sendSettingsMenu(chatId);
    }

    private void downloadFoundMedia(long chatId, String callbackId) {
        processingState.put(chatId, true);
        sendAlert(callbackId, "⏳ Завантаження...", false);
        
        executor.submit(() -> {
            try {
                List<String> allUrls = new ArrayList<>();
                synchronized (lastResults) {
                    for (ScrapeResult res : lastResults) allUrls.addAll(res.getMediaUrls());
                }

                if (allUrls.isEmpty()) {
                    sendMessageReturning(chatId, "📂 Немає файлів.", true);
                    return;
                }

                int downloaded = 0;
                for (String url : allUrls) {
                    if (scraper.performDownload(url)) downloaded++;
                }

                sendMessageReturning(chatId, "✅ **Готово!**\n💾 Збережено: " + downloaded + "\n📂 Папка: `src/Lib/`", true);
            } finally {
                processingState.put(chatId, false);
            }
        });
    }

    // --- ВІДОБРАЖЕННЯ ТЕКСТУ (ВИПРАВЛЕНО ПОМИЛКУ 400) ---
    private void viewSiteText(long chatId, int index) {
        if (index >= lastResults.size()) return;
        ScrapeResult result = lastResults.get(index);
        
        // Прибираємо **, бо без Markdown вони будуть просто зірочками
        StringBuilder text = new StringBuilder("📖 " + result.getTitle() + "\n\n");
        
        if (result.getFoundInfo().isEmpty()) {
            text.append("❌ Текст не знайдено (сайт порожній або захищений).");
        } else {
            int limit = Math.min(result.getFoundInfo().size(), 15);
            for (int i = 0; i < limit; i++) {
                text.append(result.getFoundInfo().get(i)).append("\n\n");
            }
            if (result.getFoundInfo().size() > 15) {
                text.append("... (ще ").append(result.getFoundInfo().size() - 15).append(" блоків)");
            }
        }

        String msgText = text.length() > 4000 ? text.substring(0, 4000) + "..." : text.toString();
        
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText(msgText);
        // ❗ ВАЖЛИВО: Цей рядок видалено, щоб спецсимволи з сайтів не ламали бота
        // msg.setParseMode("Markdown"); 
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (!result.getFoundInfo().isEmpty()) {
            rows.add(createRow(createButton("💾 Зберегти текст", "save_text_" + index)));
        }
        rows.add(createRow(createButton("🔙 Назад", "btn_back_to_report")));
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        
        executeMessageReturningMessage(msg);
    }

    private void performWebSearch(long chatId, String query) {
        clearTempMessage(chatId);
        processingState.put(chatId, true);
        executor.submit(() -> {
            Message loading = null;
            try {
                loading = sendMessageReturning(chatId, "🔎 **Шукаю:** `" + query + "`...", false);
                List<String[]> results = scraper.findSitesByKeyword(query);
                if (loading != null) deleteMessage(chatId, loading.getMessageId());

                if (results.isEmpty()) {
                    sendMessageReturning(chatId, "😕 Нічого не знайдено.", true);
                    userStates.remove(chatId);
                } else {
                    tempSearchResults.put(chatId, results);
                    sendSearchResults(chatId, results);
                    userStates.remove(chatId); 
                }
            } catch (Exception e) {
                if (loading != null) deleteMessage(chatId, loading.getMessageId());
                sendMessageReturning(chatId, "🔥 Помилка: " + e.getMessage(), true);
            } finally {
                processingState.put(chatId, false);
            }
        });
    }

    private void sendSearchResults(long chatId, List<String[]> results) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("🌐 **Результати:**");
        message.setParseMode("Markdown");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            String title = results.get(i)[0].length() > 30 ? results.get(i)[0].substring(0, 30) + "..." : results.get(i)[0];
            rows.add(createRow(createButton("🔗 " + title, "pick_site_" + i)));
        }
        rows.add(createRow(createButton("🔙 Скасувати", "btn_back")));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        executeMessageReturningMessage(message);
    }

    private void handleSiteSelection(long chatId, int index) {
        List<String[]> results = tempSearchResults.get(chatId);
        if (results == null || index >= results.size()) return;
        
        tempSelectedSite.put(chatId, results.get(index)[1]);
        userStates.put(chatId, "WAITING_INTERNAL_KEYWORD");
        Message msg = sendMessageReturning(chatId, "✅ Обрано: **" + results.get(index)[0] + "**\n🔑 Введіть слово для пошуку:", true);
        if (msg != null) tempHelpMessages.put(chatId, msg.getMessageId());
    }

    private void performInternalSearch(long chatId, String keyword) {
        clearTempMessage(chatId);
        String url = tempSelectedSite.get(chatId);
        if (url == null) return;
        
        userStates.remove(chatId);
        processingState.put(chatId, true);
        executor.submit(() -> {
            try {
                Message loading = sendMessageReturning(chatId, "🕵️‍♂️ **Аналізую:** `" + keyword + "`...", false);
                ScrapeResult result = scraper.processSite(url, "h1, h2, p", keyword);
                lastResults.clear();
                lastResults.add(result);
                if (loading != null) deleteMessage(chatId, loading.getMessageId());
                showScanResults(chatId);
            } finally {
                processingState.put(chatId, false);
                tempSelectedSite.remove(chatId);
            }
        });
    }

    private void showScanResults(long chatId) {
        StringBuilder report = new StringBuilder();
        int totalMediaFound = 0;
        synchronized (lastResults) {
            for (ScrapeResult result : lastResults) {
                report.append("🏷 **").append(result.getTitle()).append("**\n");
                report.append("🔗 ").append(result.getUrl()).append("\n");
                if ("SUCCESS".equals(result.getStatus())) {
                     totalMediaFound += result.getMediaUrls().size();
                     report.append("🔎 Медіа: ").append(result.getMediaUrls().size()).append("\n");
                     report.append("📝 Текст: ").append(result.getFoundInfo().size()).append(" блоків\n");
                } else { report.append("❌ Помилка: ").append(result.getStatus()).append("\n"); }
                report.append("──────────────────\n");
            }
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (totalMediaFound > 0) rows.add(createRow(createButton("📥 Завантажити медіа (" + totalMediaFound + ")", "btn_download")));
        for (int i = 0; i < lastResults.size(); i++) {
            rows.add(createRow(createButton("📝 Читати: " + (i + 1), "view_text_" + i)));
        }
        rows.add(createRow(createButton("🔙 У меню", "btn_back")));
        markup.setKeyboard(rows);
        SendMessage reportMsg = new SendMessage();
        reportMsg.setChatId(String.valueOf(chatId));
        reportMsg.setText(report.toString().isEmpty() ? "Пусто" : report.toString());
        reportMsg.setReplyMarkup(markup);
        executeMessageReturningMessage(reportMsg);
    }

    private void showGeneralReport(long chatId) {
        if (lastResults.isEmpty()) {
             Message msg = sendMessageReturning(chatId, "⚠️ **Звіт порожній.**", true);
             if (msg != null) tempHelpMessages.put(chatId, msg.getMessageId());
             return;
        }
        StringBuilder report = new StringBuilder("📊 **ЗВІТ**\n────────\n");
        int s = 0, e = 0, m = 0;
        synchronized (lastResults) {
            for (ScrapeResult r : lastResults) {
                if ("SUCCESS".equals(r.getStatus())) { s++; m += r.getMediaUrls().size(); } 
                else e++;
            }
        }
        report.append("✅ Успішно: ").append(s).append("\n❌ Помилок: ").append(e).append("\n📦 Файлів: ").append(m);
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(report.toString());
        message.setParseMode("Markdown");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(createRow(createButton("💾 Зберегти лог", "btn_save_report_file")));
        rows.add(createRow(createButton("🔙 У меню", "btn_back")));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        executeMessageReturningMessage(message);
    }

    private void saveReportToFile(long chatId, String cbId) { String f = scraper.saveGeneralReport(lastResults); if(f!=null) sendAlert(cbId, "Збережено: src/log/"+f, true); else sendAlert(cbId, "Помилка", true); }
    private void clearTempMessage(long chatId) { if(tempHelpMessages.containsKey(chatId)) { deleteMessage(chatId, tempHelpMessages.get(chatId)); tempHelpMessages.remove(chatId); } }
    private void sendAlert(String callbackId, String text, boolean showAlert) { AnswerCallbackQuery a=new AnswerCallbackQuery(); a.setCallbackQueryId(callbackId); a.setText(text); a.setShowAlert(showAlert); try{execute(a);}catch(Exception e){} }
    private void deleteMessage(long chatId, int messageId) { DeleteMessage d=new DeleteMessage(); d.setChatId(String.valueOf(chatId)); d.setMessageId(messageId); try{execute(d);}catch(Exception e){} }
    private Message sendMessageReturning(long chatId, String text, boolean withBack) { SendMessage m=new SendMessage(); m.setChatId(String.valueOf(chatId)); m.setText(text); m.setParseMode("Markdown"); if(withBack){InlineKeyboardMarkup mk=new InlineKeyboardMarkup(); List<List<InlineKeyboardButton>> r=new ArrayList<>(); r.add(createRow(createButton("🔙 Назад", "btn_back"))); mk.setKeyboard(r); m.setReplyMarkup(mk);} return executeMessageReturningMessage(m); }
    private Message executeMessageReturningMessage(SendMessage message) { try{return execute(message);}catch(Exception e){e.printStackTrace();return null;} }
    private InlineKeyboardButton createButton(String text, String callbackData) { InlineKeyboardButton b=new InlineKeyboardButton(); b.setText(text); b.setCallbackData(callbackData); return b; }
    private List<InlineKeyboardButton> createRow(InlineKeyboardButton button) { List<InlineKeyboardButton> r=new ArrayList<>(); r.add(button); return r; }

    // --- ГОЛОВНЕ МЕНЮ (ЯК ТИ ПРОСИВ - НЕ ЗМІНЮЮ) ---
    private void sendMainMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        String menuText =
                "🖥 Панель керування WebBot**\n" +
                "──────────────────────\n" +
                "🔹 **Статус системи: Очікування\n" +
                "🔹 Активних цілей: " + sites.size() + "\n" +
                "🔹 Директорія: `src/Lib/`\n" +
                "──────────────────────\n\n" +
                "👇 Оберіть операцію:";
        
        message.setText(menuText);
        message.setParseMode("Markdown");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(createRow(createButton("🛰 Побігли сканувати!", "btn_scan")));
        rows.add(createRow(createButton("🔍 Пошук у WEB (DDG)", "btn_web_search")));
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("📄 Мій звіт", "btn_report"));
        row2.add(createButton("⚙️ Налаштування", "btn_settings"));
        rows.add(row2);
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        executeMessageReturningMessage(message);
    }
}