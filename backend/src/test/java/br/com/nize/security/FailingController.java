package br.com.nize.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint that blows up with SQL-looking details, to prove 500s leak nothing. */
@RestController
@RequestMapping("/api/test")
class FailingController {

    @GetMapping("/boom")
    String boom() {
        throw new IllegalStateException("SELECT password_hash FROM app_user WHERE id = 1");
    }
}
