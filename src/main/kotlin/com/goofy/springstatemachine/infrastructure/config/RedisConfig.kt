package com.goofy.springstatemachine.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Redis 설정 클래스
 * 
 * Redis 연결 및 RedisTemplate 설정
 */
@Configuration
class RedisConfig {
    
    /**
     * RedisTemplate 빈 생성
     * 
     * 상태 머신 상태를 Redis에 저장하기 위한 RedisTemplate 설정
     */
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = connectionFactory
        
        // 키와 값의 직렬화 설정
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = GenericJackson2JsonRedisSerializer()
        
        // 해시 키와 값의 직렬화 설정
        template.hashKeySerializer = StringRedisSerializer()
        template.hashValueSerializer = GenericJackson2JsonRedisSerializer()
        
        template.afterPropertiesSet()
        return template
    }
}
