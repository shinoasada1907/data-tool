package com.universaldatatools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ToolboxApplication {

    public static void main(String[] args) {
        // Windows reports the legacy alias "Asia/Saigon". The JDBC driver sends the JVM zone on connect,
        // and postgres:17 (Debian 13) ships tzdata without legacy aliases, so it refuses the connection.
        if ("Asia/Saigon".equals(TimeZone.getDefault().getID())) {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        }
        SpringApplication.run(ToolboxApplication.class, args);
    }

}
