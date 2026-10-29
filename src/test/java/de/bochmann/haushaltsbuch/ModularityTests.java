package de.bochmann.haushaltsbuch;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(HaushaltsbuchBackendApplication.class);

	@Test
	void verifiesModularStructure() {
		modules.verify();
	}

}
