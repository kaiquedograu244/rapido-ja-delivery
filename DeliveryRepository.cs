using MySqlConnector;

namespace ApiCsharp;

public class ApiError : Exception
{
    public int StatusCode { get; }
    public ApiError(int statusCode, string message) : base(message) => StatusCode = statusCode;
}

public class DeliveryRepository
{
    private static readonly HashSet<string> FormasPagamento = new() { "PIX", "CARTAO", "DINHEIRO" };
    private const int MaxQuantidadePorProduto = 20;
    private const int MaxItensPorPedido = 30;

    private readonly string _connectionString;

    public DeliveryRepository(string connectionString) => _connectionString = connectionString;

    private async Task<MySqlConnection> AbrirAsync()
    {
        var conn = new MySqlConnection(_connectionString);
        await conn.OpenAsync();
        return conn;
    }

    // ------------------------------------------------------------------
    // Autenticação: valida o token emitido pela API Java (tabela sessoes)
    // ------------------------------------------------------------------
    public async Task<int> ExigirUsuarioAsync(HttpRequest request)
    {
        string? header = request.Headers.Authorization.FirstOrDefault();
        if (string.IsNullOrWhiteSpace(header) || !header.StartsWith("Bearer "))
            throw new ApiError(401, "Faça login para continuar");

        string token = header.Substring(7).Trim();
        if (token.Length != 64)
            throw new ApiError(401, "Sessão inválida. Faça login novamente");

        await using var conn = await AbrirAsync();
        await using var cmd = new MySqlCommand(
            "SELECT usuario_id FROM sessoes WHERE token = @token AND expira_em > NOW()", conn);
        cmd.Parameters.AddWithValue("@token", token);
        object? result = await cmd.ExecuteScalarAsync();
        if (result is null || result is DBNull)
            throw new ApiError(401, "Sessão expirada. Faça login novamente");

        return Convert.ToInt32(result);
    }

    // ------------------------------------------------------------------
    // Catálogo
    // ------------------------------------------------------------------
    public async Task<List<string>> ListarCategoriasAsync()
    {
        await using var conn = await AbrirAsync();
        await using var cmd = new MySqlCommand(
            "SELECT DISTINCT categoria FROM restaurantes WHERE ativo = 1 ORDER BY categoria", conn);
        await using var reader = await cmd.ExecuteReaderAsync();
        var lista = new List<string>();
        while (await reader.ReadAsync())
            lista.Add(reader.GetString(0));
        return lista;
    }

    public async Task<List<Restaurante>> ListarRestaurantesAsync(string? categoria, string? busca)
    {
        await using var conn = await AbrirAsync();
        var sql = "SELECT id, nome, categoria, descricao, imagem_url, taxa_entrega, tempo_min, tempo_max, avaliacao " +
                  "FROM restaurantes WHERE ativo = 1";
        await using var cmd = new MySqlCommand { Connection = conn };

        if (!string.IsNullOrWhiteSpace(categoria))
        {
            sql += " AND categoria = @categoria";
            cmd.Parameters.AddWithValue("@categoria", categoria.Trim());
        }
        if (!string.IsNullOrWhiteSpace(busca))
        {
            sql += " AND (nome LIKE @busca OR descricao LIKE @busca OR categoria LIKE @busca " +
                   "OR id IN (SELECT restaurante_id FROM produtos WHERE nome LIKE @busca))";
            cmd.Parameters.AddWithValue("@busca", "%" + busca.Trim() + "%");
        }
        cmd.CommandText = sql + " ORDER BY avaliacao DESC, nome";

        await using var reader = await cmd.ExecuteReaderAsync();
        var lista = new List<Restaurante>();
        while (await reader.ReadAsync())
            lista.Add(LerRestaurante(reader));
        return lista;
    }

    public async Task<RestauranteDetalhe?> BuscarRestauranteAsync(int id)
    {
        await using var conn = await AbrirAsync();
        Restaurante? restaurante = null;

        await using (var cmd = new MySqlCommand(
            "SELECT id, nome, categoria, descricao, imagem_url, taxa_entrega, tempo_min, tempo_max, avaliacao " +
            "FROM restaurantes WHERE id = @id AND ativo = 1", conn))
        {
            cmd.Parameters.AddWithValue("@id", id);
            await using var reader = await cmd.ExecuteReaderAsync();
            if (await reader.ReadAsync())
                restaurante = LerRestaurante(reader);
        }

        if (restaurante is null) return null;

        var produtos = new List<Produto>();
        await using (var cmd = new MySqlCommand(
            "SELECT id, restaurante_id, nome, descricao, preco FROM produtos " +
            "WHERE restaurante_id = @id AND disponivel = 1 ORDER BY id", conn))
        {
            cmd.Parameters.AddWithValue("@id", id);
            await using var reader = await cmd.ExecuteReaderAsync();
            while (await reader.ReadAsync())
            {
                produtos.Add(new Produto(
                    reader.GetInt32(0), reader.GetInt32(1), reader.GetString(2),
                    reader.GetString(3), reader.GetDecimal(4)));
            }
        }

        return new RestauranteDetalhe(restaurante, produtos);
    }

    // ------------------------------------------------------------------
    // Pedidos
    // ------------------------------------------------------------------
    public async Task<Pedido> CriarPedidoAsync(int usuarioId, CriarPedidoRequest req)
    {
        string endereco = (req.EnderecoEntrega ?? "").Trim();
        string pagamento = (req.FormaPagamento ?? "").Trim().ToUpperInvariant();
        string? observacao = string.IsNullOrWhiteSpace(req.Observacao) ? null : req.Observacao.Trim();

        if (endereco.Length < 5) throw new ApiError(400, "Informe um endereço de entrega válido");
        if (endereco.Length > 255) throw new ApiError(400, "Endereço muito longo");
        if (!FormasPagamento.Contains(pagamento)) throw new ApiError(400, "Forma de pagamento inválida");
        if (observacao is { Length: > 255 }) throw new ApiError(400, "Observação muito longa");
        if (req.Itens is null || req.Itens.Count == 0) throw new ApiError(400, "O carrinho está vazio");
        if (req.Itens.Count > MaxItensPorPedido) throw new ApiError(400, "Itens demais no pedido");

        // Agrupa quantidades por produto e valida o total agregado
        var quantidades = new Dictionary<int, int>();
        foreach (var item in req.Itens)
        {
            if (item.Quantidade <= 0) throw new ApiError(400, "Quantidade inválida");
            quantidades[item.ProdutoId] = quantidades.GetValueOrDefault(item.ProdutoId) + item.Quantidade;
            if (quantidades[item.ProdutoId] > MaxQuantidadePorProduto)
                throw new ApiError(400, $"Máximo de {MaxQuantidadePorProduto} unidades por produto");
        }

        await using var conn = await AbrirAsync();
        await using var tx = await conn.BeginTransactionAsync();

        decimal taxaEntrega;
        await using (var cmd = new MySqlCommand(
            "SELECT taxa_entrega FROM restaurantes WHERE id = @id AND ativo = 1", conn, tx))
        {
            cmd.Parameters.AddWithValue("@id", req.RestauranteId);
            object? taxa = await cmd.ExecuteScalarAsync();
            if (taxa is null || taxa is DBNull) throw new ApiError(404, "Restaurante não encontrado");
            taxaEntrega = Convert.ToDecimal(taxa);
        }

        // Preços SEMPRE vêm do banco, nunca do navegador
        var precos = new Dictionary<int, (string Nome, decimal Preco)>();
        await using (var cmd = new MySqlCommand { Connection = conn, Transaction = tx })
        {
            var nomesParametros = new List<string>();
            int i = 0;
            foreach (int produtoId in quantidades.Keys)
            {
                string nome = "@p" + i++;
                nomesParametros.Add(nome);
                cmd.Parameters.AddWithValue(nome, produtoId);
            }
            cmd.Parameters.AddWithValue("@restaurante", req.RestauranteId);
            cmd.CommandText = "SELECT id, nome, preco FROM produtos WHERE disponivel = 1 " +
                              "AND restaurante_id = @restaurante AND id IN (" + string.Join(",", nomesParametros) + ")";
            await using var reader = await cmd.ExecuteReaderAsync();
            while (await reader.ReadAsync())
                precos[reader.GetInt32(0)] = (reader.GetString(1), reader.GetDecimal(2));
        }

        if (precos.Count != quantidades.Count)
            throw new ApiError(400, "Algum produto do carrinho não está disponível neste restaurante");

        decimal subtotal = quantidades.Sum(q => precos[q.Key].Preco * q.Value);
        decimal total = subtotal + taxaEntrega;

        long pedidoId;
        await using (var cmd = new MySqlCommand(
            "INSERT INTO pedidos (usuario_id, restaurante_id, endereco_entrega, forma_pagamento, observacao, " +
            "subtotal, taxa_entrega, total, status) VALUES (@u, @r, @e, @f, @o, @s, @t, @total, 'RECEBIDO')", conn, tx))
        {
            cmd.Parameters.AddWithValue("@u", usuarioId);
            cmd.Parameters.AddWithValue("@r", req.RestauranteId);
            cmd.Parameters.AddWithValue("@e", endereco);
            cmd.Parameters.AddWithValue("@f", pagamento);
            cmd.Parameters.AddWithValue("@o", (object?)observacao ?? DBNull.Value);
            cmd.Parameters.AddWithValue("@s", subtotal);
            cmd.Parameters.AddWithValue("@t", taxaEntrega);
            cmd.Parameters.AddWithValue("@total", total);
            await cmd.ExecuteNonQueryAsync();
            pedidoId = cmd.LastInsertedId;
        }

        foreach (var (produtoId, quantidade) in quantidades)
        {
            await using var cmd = new MySqlCommand(
                "INSERT INTO itens_pedido (pedido_id, produto_id, nome_produto, quantidade, preco_unitario) " +
                "VALUES (@pedido, @produto, @nome, @qtd, @preco)", conn, tx);
            cmd.Parameters.AddWithValue("@pedido", pedidoId);
            cmd.Parameters.AddWithValue("@produto", produtoId);
            cmd.Parameters.AddWithValue("@nome", precos[produtoId].Nome);
            cmd.Parameters.AddWithValue("@qtd", quantidade);
            cmd.Parameters.AddWithValue("@preco", precos[produtoId].Preco);
            await cmd.ExecuteNonQueryAsync();
        }

        await using (var cmd = new MySqlCommand(
            "INSERT INTO historico_status (pedido_id, status) VALUES (@pedido, 'RECEBIDO')", conn, tx))
        {
            cmd.Parameters.AddWithValue("@pedido", pedidoId);
            await cmd.ExecuteNonQueryAsync();
        }

        await tx.CommitAsync();

        var pedidos = await ListarPedidosAsync(usuarioId, (int)pedidoId);
        return pedidos[0];
    }

    public async Task<List<Pedido>> ListarPedidosAsync(int usuarioId, int? pedidoId = null)
    {
        await using var conn = await AbrirAsync();
        var pedidos = new List<Pedido>();

        await using (var cmd = new MySqlCommand { Connection = conn })
        {
            var sql = "SELECT p.id, p.restaurante_id, r.nome, r.imagem_url, p.endereco_entrega, p.forma_pagamento, " +
                      "p.observacao, p.subtotal, p.taxa_entrega, p.total, p.status, p.criado_em " +
                      "FROM pedidos p JOIN restaurantes r ON r.id = p.restaurante_id WHERE p.usuario_id = @u";
            cmd.Parameters.AddWithValue("@u", usuarioId);
            if (pedidoId.HasValue)
            {
                sql += " AND p.id = @id";
                cmd.Parameters.AddWithValue("@id", pedidoId.Value);
            }
            cmd.CommandText = sql + " ORDER BY p.id DESC LIMIT 50";

            await using var reader = await cmd.ExecuteReaderAsync();
            while (await reader.ReadAsync())
            {
                pedidos.Add(new Pedido(
                    reader.GetInt32(0),
                    reader.GetInt32(1),
                    reader.GetString(2),
                    reader.GetString(3),
                    reader.GetString(4),
                    reader.GetString(5),
                    reader.IsDBNull(6) ? null : reader.GetString(6),
                    reader.GetDecimal(7),
                    reader.GetDecimal(8),
                    reader.GetDecimal(9),
                    reader.GetString(10),
                    reader.GetDateTime(11),
                    new List<ItemPedido>()));
            }
        }

        foreach (var pedido in pedidos)
        {
            await using var cmd = new MySqlCommand(
                "SELECT produto_id, nome_produto, quantidade, preco_unitario FROM itens_pedido " +
                "WHERE pedido_id = @id ORDER BY id", conn);
            cmd.Parameters.AddWithValue("@id", pedido.Id);
            await using var reader = await cmd.ExecuteReaderAsync();
            while (await reader.ReadAsync())
            {
                int qtd = reader.GetInt32(2);
                decimal preco = reader.GetDecimal(3);
                pedido.Itens.Add(new ItemPedido(reader.GetInt32(0), reader.GetString(1), qtd, preco, qtd * preco));
            }
        }

        return pedidos;
    }

    private static Restaurante LerRestaurante(MySqlDataReader r) => new(
        r.GetInt32(0), r.GetString(1), r.GetString(2), r.GetString(3), r.GetString(4),
        r.GetDecimal(5), r.GetInt32(6), r.GetInt32(7), r.GetDecimal(8));
}
