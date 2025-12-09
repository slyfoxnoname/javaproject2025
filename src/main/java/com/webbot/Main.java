package com.webbot;

import org.telegram.telegrambots.meta.TelegramBotsApi; // Переконайтеся, що імпорт відповідає назві вашого класу Бота
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import com.webbot.Bot.TelegramBot;

public class Main {
    public static void main(String[] args) {
        try {
            // 1. Ініціалізація API Telegram
            // Це створює сесію, яка буде слухати оновлення від серверів Telegram
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            // 2. Реєстрація нашого бота
            // Ми створюємо екземпляр MyBot. У цей момент всередині нього 
            // також створюється ParserService і готується планувальник.
            TelegramBot myBot = new TelegramBot();
            botsApi.registerBot(myBot);

            System.out.println("✅ Бот успішно запущено! Він готовий приймати команди.");
            
        } catch (TelegramApiException e) {
            System.err.println("❌ Помилка при запуску бота: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("❌ Непередбачена помилка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}