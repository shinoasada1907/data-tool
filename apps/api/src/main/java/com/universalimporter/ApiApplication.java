package com.universalimporter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class ApiApplication {

    public static void main(String[] args) {
        // Windows reports the legacy alias "Asia/Saigon", which PostgreSQL rejects
        // when the JDBC driver sends it as the session TimeZone.
        if ("Asia/Saigon".equals(TimeZone.getDefault().getID())) {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        }
        SpringApplication.run(ApiApplication.class, args);
    }

}
