package com.prototype1.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.prototype1.beans.EtagManager;
import com.prototype1.beans.JsonValidator;
import com.prototype1.beans.RedisImplementation;

@Configuration
public class RedisConfiguration {
	
	@Bean("jsonValidator")
	public JsonValidator JsonValidator() {
		return new JsonValidator();
	}
	
	@Bean("redisImplementation")
	public RedisImplementation RedisImplementation() {
		return new RedisImplementation() ;
	}
	
	@Bean("etagManager")
	public EtagManager etagManager() {
		return new EtagManager();
	}
	
}
