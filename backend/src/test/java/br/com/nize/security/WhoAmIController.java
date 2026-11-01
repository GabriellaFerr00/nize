package br.com.nize.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only protected endpoint: proves user_id comes from the token. */
@RestController
@RequestMapping("/api/test")
class WhoAmIController {

    @GetMapping("/whoami")
    String whoAmI() {
        return String.valueOf(CurrentUser.id());
    }
}
