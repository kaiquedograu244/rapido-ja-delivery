package com.delivery.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.delivery.auth.AuthDtos.AtualizarPerfilRequest;
import com.delivery.auth.AuthDtos.CadastroRequest;
import com.delivery.auth.AuthDtos.LoginRequest;
import com.delivery.auth.AuthDtos.RespostaAuth;
import com.delivery.auth.AuthDtos.Usuario;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "servico", "api-java");
    }

    @PostMapping("/auth/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public RespostaAuth cadastro(@Valid @RequestBody CadastroRequest req) {
        return authService.cadastrar(req);
    }

    @PostMapping("/auth/login")
    public RespostaAuth login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/auth/logout")
    public Map<String, String> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        authService.logout(auth);
        return Map.of("mensagem", "Sessão encerrada");
    }

    @GetMapping("/auth/me")
    public Usuario me(@RequestHeader(value = "Authorization", required = false) String auth) {
        return authService.exigirUsuario(auth);
    }

    @PutMapping("/auth/me")
    public Usuario atualizar(@RequestHeader(value = "Authorization", required = false) String auth,
                             @Valid @RequestBody AtualizarPerfilRequest req) {
        return authService.atualizarPerfil(auth, req);
    }
}
