package com.wince.fincore;

import org.springframework.boot.SpringApplication;

public class TestFinCoreApplication {

	public static void main(String[] args) {
		SpringApplication.from(FinCoreApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
