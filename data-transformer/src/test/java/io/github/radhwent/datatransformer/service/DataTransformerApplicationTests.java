package io.github.radhwent.datatransformer.service;

import io.github.radhwent.datatransformer.config.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DataTransformerApplicationTests {

	@Test
	void contextLoads() {
	}

}
