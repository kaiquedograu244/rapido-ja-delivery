package com.delivery.auth;

import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.delivery.auth.AuthDtos.AtualizarPerfilRequest;
import com.delivery.auth.AuthDtos.CadastroRequest;
import com.delivery.auth.AuthDtos.LoginRequest;
import com.delivery.auth.AuthDtos.RespostaAuth;
import com.delivery.auth.AuthDtos.Usuario;
import com.delivery.common.ApiException;

@Service
public class AuthService {

    private static final int DIAS_SESSAO = 7;

    private static final RowMapper<Usuario> USUARIO_MAPPER = (rs, i) -> new Usuario(
            rs.getInt("id"),
            rs.getString("nome"),
            rs.getString("email"),
            rs.getString("telefone"),
            rs.getString("endereco"),
            rs.getBoolean("admin"));

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    public AuthService(JdbcTemplate jdbc, BCryptPasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    public RespostaAuth cadastrar(CadastroRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE email = ?", Integer.class, email);
        if (existe != null && existe > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Este e-mail já está cadastrado");
        }

        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO usuarios (nome, email, senha_hash, telefone, endereco) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, req.nome().trim());
            ps.setString(2, email);
            ps.setString(3, encoder.encode(req.senha()));
            ps.setString(4, req.telefone() == null ? "" : req.telefone().trim());
            ps.setString(5, req.endereco() == null ? "" : req.endereco().trim());
            return ps;
        }, kh);

        Number key = kh.getKey();
        if (key == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível criar o usuário");
        }
        return criarSessao(buscarPorId(key.intValue()));
    }

    public RespostaAuth login(LoginRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        List<String> hashes = jdbc.queryForList("SELECT senha_hash FROM usuarios WHERE email = ?", String.class, email);
        if (hashes.isEmpty() || !encoder.matches(req.senha(), hashes.get(0))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos");
        }
        Integer id = jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", Integer.class, email);
        return criarSessao(buscarPorId(id == null ? 0 : id));
    }

    public void logout(String authorization) {
        String token = extrairToken(authorization);
        if (token != null) {
            jdbc.update("DELETE FROM sessoes WHERE token = ?", token);
        }
    }

    public Usuario atualizarPerfil(String authorization, AtualizarPerfilRequest req) {
        Usuario atual = exigirUsuario(authorization);
        jdbc.update("UPDATE usuarios SET nome = ?, telefone = ?, endereco = ? WHERE id = ?",
                req.nome().trim(),
                req.telefone() == null ? "" : req.telefone().trim(),
                req.endereco() == null ? "" : req.endereco().trim(),
                atual.id());
        return buscarPorId(atual.id());
    }

    /** Retorna o usuário dono do token ou lança 401. */
    public Usuario exigirUsuario(String authorization) {
        String token = extrairToken(authorization);
        if (token == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Faça login para continuar");
        }
        List<Usuario> lista = jdbc.query(
                "SELECT u.id, u.nome, u.email, u.telefone, u.endereco, u.admin " +
                "FROM sessoes s JOIN usuarios u ON u.id = s.usuario_id " +
                "WHERE s.token = ? AND s.expira_em > NOW()",
                USUARIO_MAPPER, token);
        if (lista.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sessão expirada. Faça login novamente");
        }
        return lista.get(0);
    }

    public Usuario exigirAdmin(String authorization) {
        Usuario u = exigirUsuario(authorization);
        if (!u.admin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Acesso restrito ao administrador");
        }
        return u;
    }

    private Usuario buscarPorId(int id) {
        List<Usuario> lista = jdbc.query(
                "SELECT id, nome, email, telefone, endereco, admin FROM usuarios WHERE id = ?", USUARIO_MAPPER, id);
        if (lista.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado");
        }
        return lista.get(0);
    }

    private RespostaAuth criarSessao(Usuario usuario) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        jdbc.update("INSERT INTO sessoes (token, usuario_id, expira_em) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? DAY))",
                token, usuario.id(), DIAS_SESSAO);
        return new RespostaAuth(token, usuario);
    }

    private static String extrairToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.length() == 64 ? token : null;
    }
}
