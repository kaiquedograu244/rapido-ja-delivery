using ApiCsharp;

var builder = WebApplication.CreateBuilder(args);

string Env(string nome, string padrao) =>
    Environment.GetEnvironmentVariable(nome) is { Length: > 0 } valor ? valor : padrao;

string connectionString =
    $"Server={Env("DB_HOST", "localhost")};Port={Env("DB_PORT", "3306")};" +
    $"Database={Env("DB_NAME", "delivery_db")};User ID={Env("DB_USER", "root")};" +
    $"Password={Env("DB_PASSWORD", "root")};SslMode=None;AllowPublicKeyRetrieval=True;CharSet=utf8mb4;";

builder.WebHost.UseUrls($"http://0.0.0.0:{Env("PORT", "5000")}");
builder.Services.AddSingleton(new DeliveryRepository(connectionString));
builder.Services.AddCors(o => o.AddDefaultPolicy(p => p.AllowAnyOrigin().AllowAnyHeader().AllowAnyMethod()));

var app = builder.Build();
app.UseCors();

// Converte erros em JSON { erro: "mensagem" }
app.Use(async (context, next) =>
{
    try
    {
        await next();
    }
    catch (ApiError ex)
    {
        context.Response.StatusCode = ex.StatusCode;
        await context.Response.WriteAsJsonAsync(new { erro = ex.Message });
    }
    catch (BadHttpRequestException)
    {
        context.Response.StatusCode = 400;
        await context.Response.WriteAsJsonAsync(new { erro = "Corpo da requisição inválido" });
    }
    catch (Exception ex)
    {
        app.Logger.LogError(ex, "Erro inesperado");
        context.Response.StatusCode = 500;
        await context.Response.WriteAsJsonAsync(new { erro = "Erro interno no servidor" });
    }
});

app.MapGet("/api/health", () => Results.Ok(new { status = "ok", servico = "api-csharp" }));

app.MapGet("/api/categorias", async (DeliveryRepository repo) =>
    Results.Ok(await repo.ListarCategoriasAsync()));

app.MapGet("/api/restaurantes", async (string? categoria, string? busca, DeliveryRepository repo) =>
    Results.Ok(await repo.ListarRestaurantesAsync(categoria, busca)));

app.MapGet("/api/restaurantes/{id:int}", async (int id, DeliveryRepository repo) =>
{
    var detalhe = await repo.BuscarRestauranteAsync(id);
    return detalhe is null
        ? Results.NotFound(new { erro = "Restaurante não encontrado" })
        : Results.Ok(detalhe);
});

app.MapPost("/api/pedidos", async (CriarPedidoRequest req, HttpRequest http, DeliveryRepository repo) =>
{
    int usuarioId = await repo.ExigirUsuarioAsync(http);
    var pedido = await repo.CriarPedidoAsync(usuarioId, req);
    return Results.Created($"/api/pedidos/{pedido.Id}", pedido);
});

app.MapGet("/api/pedidos", async (HttpRequest http, DeliveryRepository repo) =>
{
    int usuarioId = await repo.ExigirUsuarioAsync(http);
    return Results.Ok(await repo.ListarPedidosAsync(usuarioId));
});

app.MapGet("/api/pedidos/{id:int}", async (int id, HttpRequest http, DeliveryRepository repo) =>
{
    int usuarioId = await repo.ExigirUsuarioAsync(http);
    var pedidos = await repo.ListarPedidosAsync(usuarioId, id);
    return pedidos.Count == 0
        ? Results.NotFound(new { erro = "Pedido não encontrado" })
        : Results.Ok(pedidos[0]);
});

app.Run();
