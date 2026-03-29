package com.example.skyline.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA(Single Page Application) Fallback Controller
 * /api/** 를 제외한 모든 경로를 index.html 로 포워딩하여
 * React Router의 BrowserRouter 가 클라이언트에서 라우팅을 처리할 수 있도록 한다.
 */
@Controller
public class SpaController {

    @RequestMapping(value = {
            "/search",
            "/reservations",
            "/dashboard",
            "/search/**",
            "/reservations/**",
            "/dashboard/**"
    })
    public String forward() {
        return "forward:/index.html";
    }

    @RequestMapping("/metrics")
    public String forwardMetrics() {
        return "forward:/actuator/prometheus";
    }
}