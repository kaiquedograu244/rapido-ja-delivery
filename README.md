# RapidoJá Delivery

Site de delivery completo com **front-end HTML/CSS/JavaScript**, **API Java (Spring Boot 3)**, **API C# (ASP.NET Core 8)** e **banco MySQL 8**.

| Parte | Pasta | Porta | Responsabilidade |
|---|---|---|---|
| Front-end | `frontend/` | 3000 | Telas do site (restaurantes, carrinho, checkout, rastreio, painel) |
| API Java | `api-java/` | 8080 | Cadastro, login, perfil, rastreamento e status das entregas |
| API C# | `api-csharp/` | 5000 | Restaurantes, cardápio e criação/listagem de pedidos |
| MySQL | `database/schema.sql` | 3306 | Tabelas + dados iniciais |

As duas APIs usam o **mesmo banco**. O login é feito na API Java, que grava um token na tabela `sessoes`; a API C# valida esse mesmo token, então o usuário fica logado nas duas.

**Usuário administrador** (criado automaticamente pela API Java): `admin@delivery.com` / `admin123`

---

## Opção 1 — Docker (mais fácil)

Requisito: [Docker Desktop](https://www.docker.com/products/docker-desktop/).

```bash
docker compose up --build
```

Abra **http://localhost:3000**. Para parar: `Ctrl+C` e `docker compose down` (use `docker compose down -v` para apagar o banco).

---

## Opção 2 — Rodando manualmente

Requisitos: **MySQL 8**, **Java 17+** com **Maven**, **.NET SDK 8**, e Python (ou qualquer servidor estático).

### 1. Banco de dados

```bash
mysql -u root -p < database/schema.sql
```

As APIs usam por padrão `root` / `root` em `localhost:3306`. Se sua senha for outra, defina as variáveis antes de iniciar cada API:

```bash
# Linux/Mac
export DB_USER=root DB_PASSWORD=suasenha
# Windows PowerShell
$env:DB_USER="root"; $env:DB_PASSWORD="suasenha"
```

Variáveis aceitas: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`.

### 2. API Java (terminal 1)

```bash
cd api-java
mvn spring-boot:run
```

### 3. API C# (terminal 2)

```bash
cd api-csharp
dotnet run
```

### 4. Front-end (terminal 3)

```bash
cd frontend
python -m http.server 3000
```

Abra **http://localhost:3000**. (No VS Code também funciona com a extensão *Live Server*.)

---

## Como testar

1. Escolha um restaurante, adicione itens e abra o carrinho.
2. Clique em **Finalizar pedido** → crie uma conta → confirme o pedido.
3. Você vai para a tela de **rastreio** (atualiza a cada 5 s).
4. Em outra aba/navegador, entre como **admin** e acesse **Painel** para avançar o status (Em preparo → Saiu para entrega → Entregue). O rastreio do cliente atualiza sozinho.

## Endpoints

**API Java — `http://localhost:8080/api`**

| Método | Rota | Descrição |
|---|---|---|
| GET | `/health` | Status da API |
| POST | `/auth/cadastro` | `{nome, email, senha, telefone, endereco}` |
| POST | `/auth/login` | `{email, senha}` → `{token, usuario}` |
| POST | `/auth/logout` | Encerra a sessão |
| GET/PUT | `/auth/me` | Ver / atualizar perfil |
| GET | `/entregas/{pedidoId}` | Rastreamento (dono ou admin) |
| GET | `/entregas?status=` | Lista pedidos (admin) |
| PUT | `/entregas/{pedidoId}/status` | `{status}` (admin) |

**API C# — `http://localhost:5000/api`**

| Método | Rota | Descrição |
|---|---|---|
| GET | `/health` | Status da API |
| GET | `/categorias` | Categorias |
| GET | `/restaurantes?categoria=&busca=` | Lista restaurantes |
| GET | `/restaurantes/{id}` | Restaurante + cardápio |
| POST | `/pedidos` | Cria pedido (preço calculado no servidor) |
| GET | `/pedidos` | Pedidos do usuário logado |
| GET | `/pedidos/{id}` | Um pedido |

Rotas protegidas exigem o header `Authorization: Bearer <token>`.

## Segurança implementada

- Senhas com **BCrypt**; tokens de sessão aleatórios de 256 bits com expiração de 7 dias.
- Todas as consultas SQL são **parametrizadas** (sem SQL injection).
- **Preços e total calculados no servidor** a partir do banco; quantidade validada (1–20 por produto).
- Pedidos gravados em **transação**; cada usuário só vê os próprios pedidos; painel restrito a admin.

## Publicar no GitHub

```bash
git init
git add .
git commit -m "RapidoJá Delivery: front + API Java + API C# + MySQL"
git branch -M main
git remote add origin https://github.com/SEU_USUARIO/rapidoja-delivery.git
git push -u origin main
```
