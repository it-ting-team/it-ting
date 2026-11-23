package com.itting.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 예약 작업({@code @Scheduled})을 켠다. 예: 30일이 지난 알림 지우기.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

}
