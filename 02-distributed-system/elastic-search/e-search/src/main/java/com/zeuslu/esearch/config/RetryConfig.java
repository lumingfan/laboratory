package com.zeuslu.esearch.config;

import com.zeuslu.esearch.exception.NonRetryableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.context.RetryContextSupport;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Set;

@Configuration
@Slf4j
public class RetryConfig {

    public class CustomRetryPolicy implements RetryPolicy {
        private final int maxAttempts = 3;
        private final Set<Class<? extends Throwable>> nonRetryable = Set.of(
                NonRetryableException.class
        );

        @Override
        public RetryContext open(RetryContext parent) {
            return new RetryContextSupport(parent);
        }

        @Override
        public boolean canRetry(RetryContext context) {
            Throwable lastThrowable = context.getLastThrowable();
            if (lastThrowable == null) {
                return true;
            }
            for (Class<?> clazz : nonRetryable) {
                if (clazz.isInstance(lastThrowable)) {
                    return false;
                }
            }
            return context.getRetryCount() < maxAttempts;
        }

        @Override
        public void close(RetryContext context) {}

        @Override
        public void registerThrowable(RetryContext context, Throwable throwable) {
            RetryContextSupport ctx = (RetryContextSupport) context;
            ctx.registerThrowable(throwable);
        }
    }

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate template = new RetryTemplate();

        // 1. 设置最大重试次数
        template.setRetryPolicy(new CustomRetryPolicy());


        // 2. 设置指数退避策略
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(10000);
        template.setBackOffPolicy(backOffPolicy);

        // 3. 【关键】设置监听器，用于记录重试日志
        template.registerListener(new RetryListener() {
            @Override
            public <T, E extends Throwable> void onError(
                    RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                log.warn("重试第 {} 次，异常：{}", context.getRetryCount(), throwable.getMessage());
            }
        });

        return template;
    }
}