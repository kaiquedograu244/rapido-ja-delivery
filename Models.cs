namespace ApiCsharp;

public record Restaurante(
    int Id,
    string Nome,
    string Categoria,
    string Descricao,
    string ImagemUrl,
    decimal TaxaEntrega,
    int TempoMin,
    int TempoMax,
    decimal Avaliacao);

public record Produto(int Id, int RestauranteId, string Nome, string Descricao, decimal Preco);

public record RestauranteDetalhe(Restaurante Restaurante, List<Produto> Produtos);

public record ItemPedidoRequest(int ProdutoId, int Quantidade);

public record CriarPedidoRequest(
    int RestauranteId,
    string? EnderecoEntrega,
    string? FormaPagamento,
    string? Observacao,
    List<ItemPedidoRequest>? Itens);

public record ItemPedido(int ProdutoId, string NomeProduto, int Quantidade, decimal PrecoUnitario, decimal Total);

public record Pedido(
    int Id,
    int RestauranteId,
    string Restaurante,
    string ImagemUrl,
    string EnderecoEntrega,
    string FormaPagamento,
    string? Observacao,
    decimal Subtotal,
    decimal TaxaEntrega,
    decimal Total,
    string Status,
    DateTime CriadoEm,
    List<ItemPedido> Itens);
