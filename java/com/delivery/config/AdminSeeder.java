package com.delivery.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Garante que exista um usuário administrador para acessar o painel do restaurante.
 * Login: admin@delivery.com / admin123
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
    private static final String ADMIN_EMAIL = "admin@delivery.com";

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder;

    public AdminSeeder(JdbcTemplate jdbc, BCryptPasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuarios WHERE email = ?", Integer.class, ADMIN_EMAIL);
        if (total == null || total == 0) {
            jdbc.update(
                    "INSERT INTO usuarios (nome, email, senha_hash, telefone, endereco, admin) VALUES (?, ?, ?, ?, ?, 1)",
                    "Administrador", ADMIN_EMAIL, encoder.encode("admin123"), "", "");
            log.info("Usuario admin criado: {} / admin123", ADMIN_EMAIL);
        }
    }
}
