package com.kenyarealestate.payment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication @EnableRetry @EnableScheduling
public class PaymentServiceApplication { public static void main(String[] a) { SpringApplication.run(PaymentServiceApplication.class,a); } }
