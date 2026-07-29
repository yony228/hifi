package com.hify.controller;

import com.hify.common.dto.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口。
 * <p>
 * 区别于 Actuator {@code /actuator/health}（基础设施层探活），
 * 此接口验证业务层完整链路：Spring 容器 → 统一响应 → JSON 序列化。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HealthController {

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("Hify is running");
    }
}
