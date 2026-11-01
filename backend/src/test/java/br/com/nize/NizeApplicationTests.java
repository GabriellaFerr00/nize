package br.com.nize;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("h2")
class NizeApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsWithH2Profile() {
        assertThat(context.getId()).isNotBlank();
    }
}
