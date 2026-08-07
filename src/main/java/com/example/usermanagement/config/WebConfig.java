package com.example.usermanagement.config;

import java.nio.file.Path;

import com.example.usermanagement.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final Path uploadDirectory;

    public WebConfig(LoginInterceptor loginInterceptor,
                     @Value("${app.upload-dir}") String uploadDirectory) {
        this.loginInterceptor = loginInterceptor;
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/users/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String resourceLocation = uploadDirectory.toUri().toString();
        if (!resourceLocation.endsWith("/")) {
            resourceLocation += "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(resourceLocation);
    }
}
