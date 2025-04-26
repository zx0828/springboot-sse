package cn.zuster.sse.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RefreshScope
public class ConfigController {
    
    @Value("${demo.config.message:Local default message}")
    private String configMessage;

    @GetMapping("/getConfig")
    public String getConfig() {
        return "From Nacos Config: " + configMessage;
    }
}