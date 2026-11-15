package com.delivery.entrega;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.delivery.auth.AuthDtos.Usuario;
import com.delivery.auth.AuthService;
import com.delivery.common.ApiException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Rastreamento de entregas e painel do restaurante.
 * Os pedidos são criados pela API C#; aqui acompanhamos e atualizamos o status.
 */
@RestController
@RequestMapping("/api/entregas")
public class EntregaController {

    public static final List<String> FLUXO = List.of("RECEBIDO", "PREPARANDO", "A_CAMINHO", "ENTREGUE");
    private static final Set<String> STATUS_VALIDOS = Set.of("RECEBIDO", "PREPARANDO", "A_CAMINHO", "ENTREGUE", "CANCELADO");

    public record Historico(String status, LocalDateTime criadoEm) {
    }

    public record Entrega(int pedidoId, String status, String restaurante, String cliente, String enderecoEntrega,
                          String formaPagamento, BigDecimal total, LocalDateTime criadoEm, LocalDateTime atualizadoEm,
                          List<Historico> historico) {
    }

    public record AtualizarStatusRequest(@NotBlank(message = "Informe o status") String status) {
    }

    private static final RowMapper<Historico> HISTORICO_MAPPER = (rs, i) -> new Historico(
            rs.getString("status"),
            rs.getTimestamp("criado_em").toLocalDateTime());

    private static final String SELECT_ENTREGA =
            "SELECT p.id, p.status, p.endereco_entrega, p.forma_pagamento, p.total, p.criado_em, p.atualizado_em, " +
            "p.usuario_id, r.nome AS restaurante, u.nome AS cliente " +
            "FROM pedidos p JOIN restaurantes r ON r.id = p.restaurante_id JOIN usuarios u ON u.id = p.usuario_id ";

    private final JdbcTemplate jdbc;
    private final AuthService authService;

    public EntregaController(JdbcTemplate jdbc, AuthService authService) {
        this.jdbc = jdbc;
        this.authService = authService;
    }

    /** Rastreamento de um pedido (dono do pedido ou admin). */
    @GetMapping("/{pedidoId}")
    public Entrega rastrear(@PathVariable int pedidoId,
                            @RequestHeader(value = "Authorization", required = false) String auth) {
        Usuario usuario = authService.exigirUsuario(auth);
        List<Integer> donos = jdbc.queryForList("SELECT usuario_id FROM pedidos WHERE id = ?", Integer.class, pedidoId);
        if (donos.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Pedido não encontrado");
        }
        if (!usuario.admin() && donos.get(0) != usuario.id()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Este pedido não pertence a você");
        }
        return buscar(pedidoId);
    }

    /** Lista de pedidos para o painel do restaurante (admin). */
    @GetMapping
    public List<Entrega> listar(@RequestParam(required = false) String status,
                                @RequestHeader(value = "Authorization", required = false) String auth) {
        authService.exigirAdmin(auth);
        List<Integer> ids;
        if (status != null && !status.isBlank()) {
            ids = jdbc.queryForList("SELECT id FROM pedidos WHERE status = ? ORDER BY id DESC LIMIT 100",
                    Integer.class, status.trim().toUpperCase());
        } else {
            ids = jdbc.queryForList("SELECT id FROM pedidos ORDER BY id DESC LIMIT 100", Integer.class);
        }
        return ids.stream().map(this::buscar).toList();
    }

    /** Atualiza o status do pedido (admin). */
    @PutMapping("/{pedidoId}/status")
    @Transactional
    public Entrega atualizarStatus(@PathVariable int pedidoId,
                                   @Valid @RequestBody AtualizarStatusRequest req,
                                   @RequestHeader(value = "Authorization", required = false) String auth) {
        authService.exigirAdmin(auth);
        String novo = req.status().trim().toUpperCase();
        if (!STATUS_VALIDOS.contains(novo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Status inválido: " + novo);
        }
        List<String> atual = jdbc.queryForList("SELECT status FROM pedidos WHERE id = ? FOR UPDATE", String.class, pedidoId);
        if (atual.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Pedido não encontrado");
        }
        String statusAtual = atual.get(0);
        if ("ENTREGUE".equals(statusAtual) || "CANCELADO".equals(statusAtual)) {
            throw new ApiException(HttpStatus.CONFLICT, "Pedido já finalizado (" + statusAtual + ")");
        }
        if (!novo.equals(statusAtual)) {
            jdbc.update("UPDATE pedidos SET status = ? WHERE id = ?", novo, pedidoId);
            jdbc.update("INSERT INTO historico_status (pedido_id, status) VALUES (?, ?)", pedidoId, novo);
        }
        return buscar(pedidoId);
    }

    private Entrega buscar(int pedidoId) {
        List<Historico> historico = jdbc.query(
                "SELECT status, criado_em FROM historico_status WHERE pedido_id = ? ORDER BY id",
                HISTORICO_MAPPER, pedidoId);
        List<Entrega> lista = jdbc.query(SELECT_ENTREGA + "WHERE p.id = ?", (rs, i) -> new Entrega(
                rs.getInt("id"),
                rs.getString("status"),
                rs.getString("restaurante"),
                rs.getString("cliente"),
                rs.getString("endereco_entrega"),
                rs.getString("forma_pagamento"),
                rs.getBigDecimal("total"),
                rs.getTimestamp("criado_em").toLocalDateTime(),
                rs.getTimestamp("atualizado_em").toLocalDateTime(),
                historico), pedidoId);
        if (lista.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Pedido não encontrado");
        }
        return lista.get(0);
    }
}
