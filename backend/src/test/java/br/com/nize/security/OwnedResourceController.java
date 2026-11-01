package br.com.nize.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Test-only: a resource owned by {ownerId}; anyone else gets 404 (D-6). */
@RestController
@RequestMapping("/api/test")
class OwnedResourceController {

    @GetMapping("/owned/{ownerId}")
    String owned(@PathVariable long ownerId) {
        if (CurrentUser.id() != ownerId) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "ok";
    }
}
