-- =====================================================================
--  RapidoJá Delivery - Banco de dados MySQL 8
--  Executar:  mysql -u root -p < database/schema.sql
--  ATENÇÃO: este script recria as tabelas (apaga os dados existentes).
-- =====================================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS delivery_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE delivery_db;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS historico_status;
DROP TABLE IF EXISTS itens_pedido;
DROP TABLE IF EXISTS pedidos;
DROP TABLE IF EXISTS produtos;
DROP TABLE IF EXISTS restaurantes;
DROP TABLE IF EXISTS sessoes;
DROP TABLE IF EXISTS usuarios;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- Usuários (gerenciados pela API Java)
-- ---------------------------------------------------------------------
CREATE TABLE usuarios (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  nome        VARCHAR(120) NOT NULL,
  email       VARCHAR(160) NOT NULL UNIQUE,
  senha_hash  VARCHAR(100) NOT NULL,
  telefone    VARCHAR(30)  NOT NULL DEFAULT '',
  endereco    VARCHAR(255) NOT NULL DEFAULT '',
  admin       TINYINT(1)   NOT NULL DEFAULT 0,
  criado_em   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Sessões (token gerado pela API Java e validado pelas duas APIs)
CREATE TABLE sessoes (
  token       CHAR(64)  PRIMARY KEY,
  usuario_id  INT       NOT NULL,
  criado_em   DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expira_em   DATETIME  NOT NULL,
  CONSTRAINT fk_sessao_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Catálogo (gerenciado pela API C#)
-- ---------------------------------------------------------------------
CREATE TABLE restaurantes (
  id             INT AUTO_INCREMENT PRIMARY KEY,
  nome           VARCHAR(120)  NOT NULL,
  categoria      VARCHAR(60)   NOT NULL,
  descricao      VARCHAR(255)  NOT NULL DEFAULT '',
  imagem_url     VARCHAR(255)  NOT NULL DEFAULT '',
  taxa_entrega   DECIMAL(10,2) NOT NULL DEFAULT 0,
  tempo_min      INT           NOT NULL DEFAULT 30,
  tempo_max      INT           NOT NULL DEFAULT 45,
  avaliacao      DECIMAL(2,1)  NOT NULL DEFAULT 5.0,
  ativo          TINYINT(1)    NOT NULL DEFAULT 1
) ENGINE=InnoDB;

CREATE TABLE produtos (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  restaurante_id  INT           NOT NULL,
  nome            VARCHAR(120)  NOT NULL,
  descricao       VARCHAR(255)  NOT NULL DEFAULT '',
  preco           DECIMAL(10,2) NOT NULL,
  disponivel      TINYINT(1)    NOT NULL DEFAULT 1,
  CONSTRAINT fk_produto_restaurante FOREIGN KEY (restaurante_id) REFERENCES restaurantes(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Pedidos (criados pela API C#, status atualizado pela API Java)
-- ---------------------------------------------------------------------
CREATE TABLE pedidos (
  id                INT AUTO_INCREMENT PRIMARY KEY,
  usuario_id        INT           NOT NULL,
  restaurante_id    INT           NOT NULL,
  endereco_entrega  VARCHAR(255)  NOT NULL,
  forma_pagamento   VARCHAR(30)   NOT NULL,
  observacao        VARCHAR(255)  NULL,
  subtotal          DECIMAL(10,2) NOT NULL,
  taxa_entrega      DECIMAL(10,2) NOT NULL,
  total             DECIMAL(10,2) NOT NULL,
  status            VARCHAR(20)   NOT NULL DEFAULT 'RECEBIDO',
  criado_em         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  atualizado_em     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_pedido_usuario     FOREIGN KEY (usuario_id)     REFERENCES usuarios(id),
  CONSTRAINT fk_pedido_restaurante FOREIGN KEY (restaurante_id) REFERENCES restaurantes(id),
  INDEX idx_pedido_usuario (usuario_id),
  INDEX idx_pedido_status (status)
) ENGINE=InnoDB;

CREATE TABLE itens_pedido (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  pedido_id       INT           NOT NULL,
  produto_id      INT           NOT NULL,
  nome_produto    VARCHAR(120)  NOT NULL,
  quantidade      INT           NOT NULL,
  preco_unitario  DECIMAL(10,2) NOT NULL,
  CONSTRAINT fk_item_pedido  FOREIGN KEY (pedido_id)  REFERENCES pedidos(id) ON DELETE CASCADE,
  CONSTRAINT fk_item_produto FOREIGN KEY (produto_id) REFERENCES produtos(id)
) ENGINE=InnoDB;

CREATE TABLE historico_status (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  pedido_id  INT          NOT NULL,
  status     VARCHAR(20)  NOT NULL,
  criado_em  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_historico_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Dados iniciais
-- (o usuário admin@delivery.com / admin123 é criado pela API Java ao iniciar)
-- ---------------------------------------------------------------------
INSERT INTO restaurantes (nome, categoria, descricao, imagem_url, taxa_entrega, tempo_min, tempo_max, avaliacao) VALUES
('Pizzaria Bella Napoli', 'Pizza',      'Pizzas artesanais em forno a lenha',          'img/pizzaria.png',   6.90, 35, 50, 4.8),
('Burger House',          'Lanches',    'Hambúrgueres smash e batatas crocantes',      'img/burger.png',     5.00, 25, 40, 4.7),
('Sushi Zen',             'Japonesa',   'Sushis, temakis e pratos quentes',            'img/sushi.png',      8.90, 40, 60, 4.9),
('Sabor Caseiro',         'Brasileira', 'Comida caseira feita todos os dias',          'img/brasileira.png', 4.50, 30, 45, 4.6),
('Doce Encanto',          'Doces',      'Bolos, brigadeiros e sobremesas',             'img/doces.png',      3.90, 20, 35, 4.8),
('Verde Vida',            'Saudável',   'Saladas, bowls e sucos naturais',             'img/saudavel.png',   5.90, 25, 40, 4.5);

INSERT INTO produtos (restaurante_id, nome, descricao, preco) VALUES
(1, 'Pizza Margherita',        'Molho de tomate, muçarela de búfala e manjericão', 54.90),
(1, 'Pizza Calabresa',         'Calabresa fatiada, cebola roxa e azeitonas',       49.90),
(1, 'Pizza Quatro Queijos',    'Muçarela, provolone, parmesão e gorgonzola',       59.90),
(1, 'Pizza Portuguesa',        'Presunto, ovos, cebola, ervilha e muçarela',       56.90),
(1, 'Refrigerante 2L',         'Coca-Cola, Guaraná ou Soda',                       12.00),

(2, 'Smash Clássico',          'Pão brioche, 2 smash 80g, queijo cheddar e molho', 32.90),
(2, 'Bacon Lover',             'Blend 160g, bacon crocante, cheddar e barbecue',   38.90),
(2, 'Veggie Burger',           'Hambúrguer de grão-de-bico, alface e tomate',      34.90),
(2, 'Batata Rústica',          'Porção de batata com alecrim e páprica',           18.90),
(2, 'Milkshake de Chocolate',  '400ml com calda de chocolate belga',               19.90),

(3, 'Combo 20 Peças',          'Sashimi, niguiri, uramaki e hossomaki',            79.90),
(3, 'Temaki Salmão',           'Salmão fresco, cream cheese e cebolinha',          29.90),
(3, 'Hot Roll (10 un.)',       'Uramaki empanado com salmão e cream cheese',       32.90),
(3, 'Yakisoba de Frango',      'Macarrão oriental com legumes e frango',           42.90),

(4, 'Feijoada Completa',       'Acompanha arroz, couve, farofa e laranja',         44.90),
(4, 'Frango Grelhado',         'Arroz, feijão, salada e batata frita',             32.90),
(4, 'Parmegiana de Carne',     'Arroz, fritas e molho de tomate caseiro',          39.90),
(4, 'Suco Natural 500ml',      'Laranja, limão ou maracujá',                        9.90),

(5, 'Bolo de Pote Ninho',      'Bolo de chocolate com creme de leite Ninho',       16.90),
(5, 'Caixa 12 Brigadeiros',    'Brigadeiros gourmet sortidos',                     36.00),
(5, 'Pudim de Leite',          'Fatia generosa com calda de caramelo',             12.90),
(5, 'Torta de Limão',          'Fatia com merengue maçaricado',                    14.90),

(6, 'Bowl de Frango',          'Quinoa, frango, abacate, tomate e molho de iogurte',36.90),
(6, 'Salada Caesar',           'Alface romana, croutons, parmesão e frango',       29.90),
(6, 'Wrap Integral',           'Peito de peru, cottage e folhas verdes',           24.90),
(6, 'Suco Detox',              'Couve, maçã, limão e gengibre (400ml)',            13.90);
