package com.webbot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import com.webbot.Bot.TelegramInterface;

public class Main {
    public static void main(String[] args) {
        try {
            // Инициализация API Телеграма
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            
            // Регистрация нашего бота
            botsApi.registerBot(new TelegramInterface());
            
            System.out.println("Telegram bot successfully started!");
        } catch (Exception e) {
            e.printStackTrace();    
        }
    }
}