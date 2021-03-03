package com.reactivechat;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class App {
    
    public static void main(String[] args) {
        new SpringApplicationBuilder(App.class)
            .web(false)
            .run(args);
    }
    
}