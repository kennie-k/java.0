package com.kenyarealestate.user.service;

public interface EmailService {
    void send(String to, String subject, String body);
}
