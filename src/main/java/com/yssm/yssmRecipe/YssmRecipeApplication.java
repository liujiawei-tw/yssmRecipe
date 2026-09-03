package com.yssm.yssmRecipe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class YssmRecipeApplication {

	public static void main(String[] args) {
		SpringApplication.run(YssmRecipeApplication.class, args);
	}

}
