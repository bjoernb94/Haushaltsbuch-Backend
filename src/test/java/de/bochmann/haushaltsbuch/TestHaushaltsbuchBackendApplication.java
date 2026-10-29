package de.bochmann.haushaltsbuch;

import org.springframework.boot.SpringApplication;

public class TestHaushaltsbuchBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(HaushaltsbuchBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
