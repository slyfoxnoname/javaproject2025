package com.webbot.Bot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.*;

public class TelegramBot extends TelegramLongPollingBot {

    private final String BOT_USERNAME = "WebMehSercheBot";
    private final String BOT_TOKEN = "8401459661:AAFYnUidQYUM0IsTDzlkwNSyRKW-LSK81U4";

    // Зберігаємо стан користувача
    private final Map<Long, BotState> userStates = new HashMap<>();

    // Зберігаємо ID повідомлень для очищення чату
    private final Map<Long, List<Integer>> chatMessageIds = new HashMap<>();

    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new TelegramBot());
            System.out.println("Bot started.");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update == null) return;

        Long chatId = null;

        if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
            handleCallback(update.getCallbackQuery().getData(), chatId);
        } else if (update.hasMessage()) {
            Message msg = update.getMessage();
            chatId = msg.getChatId();
            String text = msg.getText();

            BotState state = userStates.getOrDefault(chatId, BotState.START);

            switch (state) {
                default:
                    if ("/start".equals(text)) sendWelcomeMessage(chatId);
                    break;
            }
        }
    }

    // --- Стани користувача ---
    enum BotState {
        START
    }

    // --- Привітальне повідомлення ---
    private void sendWelcomeMessage(Long chatId) {
        clearChat(chatId);
        sendMessageWithSave(chatId, "Привіт! Мене звуть "+ BOT_USERNAME + ". \nОберіть спосіб пошуку:");
    }

    // --- Основне меню ---
    private InlineKeyboardMarkup getMainMenuKeyboard() {
        InlineKeyboardButton btnOption1 = InlineKeyboardButton.builder()
                .text("Ввести список сайтів")
                .callbackData("enter_sites")
                .build();
        InlineKeyboardButton btnOption2 = InlineKeyboardButton.builder()
                .text("Надіслати звіт")
                .callbackData("send_report")
                .build();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(btnOption1);
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(btnOption2);

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row1);
        keyboard.add(row2);

        return InlineKeyboardMarkup.builder().keyboard(keyboard).build();
    }

    // --- Обробка кнопок ---
    private void handleCallback(String data, Long chatId) {
        clearChat(chatId); // видаляємо попередні повідомлення

        switch (data) {
            case "enter_sites":
                sendMessageWithSave(chatId, "Введіть список сайтів:");
                break;
            case "send_report":
                sendMessageWithSave(chatId, "Тут буде звіт про виконану роботу.");
                break;
            default:
                sendMessageWithSave(chatId, "Невідома команда.");
                break;
        }
    }

    // --- Надсилання повідомлення та збереження ID ---
    private void sendMessageWithSave(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode(ParseMode.HTML)
                .replyMarkup(getMainMenuKeyboard())
                .build();
        try {
            Message sentMessage = execute(message);
            chatMessageIds.computeIfAbsent(chatId, k -> new ArrayList<>()).add(sentMessage.getMessageId());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // --- Видалення всіх попередніх повідомлень ---
    private void clearChat(Long chatId) {
        List<Integer> messageIds = chatMessageIds.getOrDefault(chatId, new ArrayList<>());
        for (Integer messageId : messageIds) {
            try {
                execute(DeleteMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .messageId(messageId)
                        .build());
            } catch (TelegramApiException e) {
                System.err.println("Не вдалося видалити повідомлення ID: " + messageId);
            }
        }
        chatMessageIds.put(chatId, new ArrayList<>()); // очищаємо список після видалення
    }
}
