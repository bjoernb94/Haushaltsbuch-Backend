package de.bochmann.haushaltsbuch.shared.systemstatus;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Prüft die Stage gegen die echte {@code application.yml}. */
class StageTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withInitializer(new ConfigDataApplicationContextInitializer())
			.withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
			.withUserConfiguration(SystemstatusConfig.class)
			.withPropertyValues("KEYCLOAK_ISSUER_URI=http://keycloak.test/realms/haushaltsbuch");

	@Test
	void devHatDefault() {
		runner.withPropertyValues("spring.profiles.active=dev").run(context ->
				assertThat(stage(context.getBean(InfoContributor.class))).isEqualTo(Stage.DEV));
	}

	@Test
	void prodNimmtWertAusDerVariablen() {
		runner.withPropertyValues("spring.profiles.active=prod", "HAUSHALTSBUCH_STAGE=PROD").run(context ->
				assertThat(stage(context.getBean(InfoContributor.class))).isEqualTo(Stage.PROD));
	}

	@Test
	void prodStartetNichtOhneStage() {
		runner.withPropertyValues("spring.profiles.active=prod").run(context ->
				assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("HAUSHALTSBUCH_STAGE"));
	}

	@Test
	void unbekannteStageVerhindertDenStart() {
		runner.withPropertyValues("spring.profiles.active=prod", "HAUSHALTSBUCH_STAGE=PRD").run(context ->
				assertThat(context).hasFailed());
	}

	private static Object stage(InfoContributor contributor) {
		Info.Builder info = new Info.Builder();
		contributor.contribute(info);
		return ((Map<?, ?>) info.build().get("app")).get("stage");
	}

}
