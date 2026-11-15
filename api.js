(function () {
  const CHAVE_SESSAO = "rapidoja_sessao";

  const Sessao = {
    obter() {
      try {
        return JSON.parse(localStorage.getItem(CHAVE_SESSAO)) || null;
      } catch {
        return null;
      }
    },
    salvar(dados) {
      localStorage.setItem(CHAVE_SESSAO, JSON.stringify(dados));
    },
    atualizarUsuario(usuario) {
      const s = Sessao.obter();
      if (s) Sessao.salvar({ ...s, usuario });
    },
    limpar() {
      localStorage.removeItem(CHAVE_SESSAO);
    },
    token() {
      const s = Sessao.obter();
      return s ? s.token : null;
    },
    usuario() {
      const s = Sessao.obter();
      return s ? s.usuario : null;
    }
  };

  async function requisicao(base, caminho, opcoes = {}) {
    const headers = { "Content-Type": "application/json" };
    const token = Sessao.token();
    if (token) headers.Authorization = "Bearer " + token;

    let resposta;
    try {
      resposta = await fetch(base + caminho, {
        method: opcoes.method || "GET",
        headers,
        body: opcoes.body ? JSON.stringify(opcoes.body) : undefined
      });
    } catch {
      const nome = base === window.CONFIG.API_JAVA ? "API Java (porta 8080)" : "API C# (porta 5000)";
      throw new Error("Não foi possível conectar à " + nome + ". Verifique se ela está rodando.");
    }

    let dados = null;
    const texto = await resposta.text();
    if (texto) {
      try {
        dados = JSON.parse(texto);
      } catch {
        dados = null;
      }
    }

    if (!resposta.ok) {
      if (resposta.status === 401 && token) {
        Sessao.limpar();
        window.dispatchEvent(new Event("sessao-alterada"));
      }
      throw new Error((dados && dados.erro) || "Erro " + resposta.status);
    }
    return dados;
  }

  const java = (c, o) => requisicao(window.CONFIG.API_JAVA, c, o);
  const csharp = (c, o) => requisicao(window.CONFIG.API_CSHARP, c, o);

  window.Sessao = Sessao;
  window.Api = {
    // API Java
    cadastro: (dados) => java("/auth/cadastro", { method: "POST", body: dados }),
    login: (dados) => java("/auth/login", { method: "POST", body: dados }),
    logout: () => java("/auth/logout", { method: "POST" }),
    me: () => java("/auth/me"),
    atualizarPerfil: (dados) => java("/auth/me", { method: "PUT", body: dados }),
    rastrear: (id) => java("/entregas/" + id),
    listarEntregas: (status) => java("/entregas" + (status ? "?status=" + encodeURIComponent(status) : "")),
    atualizarStatus: (id, status) => java("/entregas/" + id + "/status", { method: "PUT", body: { status } }),

    // API C#
    categorias: () => csharp("/categorias"),
    restaurantes: (categoria, busca) => {
      const p = new URLSearchParams();
      if (categoria) p.set("categoria", categoria);
      if (busca) p.set("busca", busca);
      const q = p.toString();
      return csharp("/restaurantes" + (q ? "?" + q : ""));
    },
    restaurante: (id) => csharp("/restaurantes/" + id),
    criarPedido: (dados) => csharp("/pedidos", { method: "POST", body: dados }),
    meusPedidos: () => csharp("/pedidos")
  };
})();
