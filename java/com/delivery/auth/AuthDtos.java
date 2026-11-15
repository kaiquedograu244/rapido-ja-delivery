package com.delivery.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record CadastroRequest(
            @NotBlank(message = "Informe seu nome") @Size(max = 120, message = "Nome muito longo") String nome,
            @NotBlank(message = "Informe seu e-mail") @Email(message = "E-mail inválido") @Size(max = 160) String email,
            @NotBlank(message = "Informe uma senha") @Size(min = 6, max = 72, message = "A senha deve ter entre 6 e 72 caracteres") String senha,
            @Size(max = 30, message = "Telefone muito longo") String telefone,
            @Size(max = 255, message = "Endereço muito longo") String endereco) {
    }

    public record LoginRequest(
            @NotBlank(message = "Informe seu e-mail") String email,
            @NotBlank(message = "Informe sua senha") String senha) {
    }

    public record AtualizarPerfilRequest(
            @NotBlank(message = "Informe seu nome") @Size(max = 120) String nome,
            @Size(max = 30) String telefone,
            @Size(max = 255) String endereco) {
    }

    public record Usuario(int id, String nome, String email, String telefone, String endereco, boolean admin) {
    }

    public record RespostaAuth(String token, Usuario usuario) {
    }
}
