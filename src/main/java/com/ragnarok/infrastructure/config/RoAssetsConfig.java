package com.ragnarok.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RoAssetsConfig implements WebMvcConfigurer {

    @Value("${ro.assets.external-path}")
    private String externalPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/ro-assets/**")
                .addResourceLocations("file:///" + externalPath + "/");
    }
}
