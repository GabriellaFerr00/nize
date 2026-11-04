# tasks.md
> Nize — Version 0.1.3 (MVP web) — 2026-11-01

## Legend
- [ ] Not started
- [~] In progress
- [x] Complete
- [!] Blocked — reason noted inline

Regra do PDF: cada fase é uma fatia completa (banco → API → tela) e as regras de cálculo viram **testes antes do código**. Cada fase ganha uma spec curta própria antes de começar. Resolver as Open Questions de `design.md` citadas em cada fase antes de iniciá-la.

Nomes em inglês: ver "Nomenclatura em inglês" em `design.md` (os termos em português nas tarefas mapeiam para a tabela).

---

## Phase 1: Fundação
*Goal*: Projeto roda, usuária cria conta e entra, e nenhuma usuária enxerga dados de outra.
*Antes de começar*: resolver Open Questions de versão do Spring Boot, groupId e recuperação de senha.

- [x] **TASK-001** [NFR-001, NFR-003]: Criar o monorepo `nize/` com `backend/` (Maven, Java 25, Spring Boot, Flyway, JUnit 5, AssertJ) e pacote vazio `br.com.nize.rules` (Spring Boot 4.0.9, groupId `br.com.nize`).
  - _Output_: `pom.xml`, classe principal, perfis `local` (MySQL) e `h2`.
  - _Verify_: `mvn -f backend/pom.xml test` verde; app sobe com perfil `h2`.
- [x] **TASK-002** [NFR-001]: Teste de arquitetura que proíbe `double`, `float` e `BigDecimal` em campos de valor monetário e proíbe Spring/JPA dentro de `nize.regras`.
  - _Output_: teste (ArchUnit ou equivalente).
  - _Verify_: `mvn test` verde; adicionar um `double valor` de teste faz falhar.
- [x] **TASK-003** [REQ-001, REQ-004, NFR-002]: Migração Flyway V1 com `usuario`, `configuracao`, `ano`, `token_recuperacao_senha` (SQL compatível com MySQL e H2).
  - _Output_: `V1__foundation.sql` (tabelas `app_user`, `settings`, `user_year`, `password_reset_token`).
  - _Verify_: app sobe nos dois perfis sem erro de migração. Verificado em 2026-11-01: H2 e MySQL 8.4 (perfil `local`, V1 aplicada e `ddl-auto: validate` ok).
- [x] **TASK-004** [REQ-001, NFR-005]: Cadastro de usuária (`POST /api/auth/cadastro`) com BCrypt, validação e e-mail único; cria `configuracao` e o ano corrente.
  - _Output_: entidade, repositório, serviço, controller, testes.
  - _Verify_: testes de cadastro válido, e-mail repetido e senha nunca em texto puro no banco.
- [x] **TASK-005** [REQ-002, NFR-005]: Login com JWT (`POST /api/auth/login`) e filtro de segurança que extrai `usuario_id` do token.
  - _Output_: `seguranca/`, controller, testes.
  - _Verify_: credenciais certas → token; erradas → 401 igual para e-mail e senha inexistentes; rota protegida sem token → 401.
- [x] **TASK-006** [REQ-003, NFR-005]: Recuperação e redefinição de senha com token de uso único e expiração, atrás da interface `EnviadorDeEmail`.
  - _Output_: serviço, endpoints, implementação de log para dev.
  - _Verify_: token válido redefine a senha; reutilizar ou expirar → erro; senha antiga deixa de funcionar.
- [x] **TASK-007** [REQ-004, NFR-004]: Base de isolamento: utilitário de teste que cria duas usuárias e um helper que verifica 404 cruzado; aplicar a `GET /api/me` e `configuracao`.
  - _Output_: `IsolamentoTestBase`, `GET /api/me`, `GET/PUT /api/configuracao`.
  - _Verify_: usuária B não lê nem altera a configuração de A.
- [x] **TASK-008** [REQ-006, REQ-009]: Endpoints de configuração (ano vigente e parcelas automáticas).
  - _Output_: serviço e testes.
  - _Verify_: alterar ano vigente e flag persiste e é devolvido no GET.
- [x] **TASK-009** [REQ-043, REQ-045, REQ-046, NFR-006]: Criar `frontend/` (Vite, React, TS, Tailwind, React Router, TanStack Query) com layout de navegação lateral/inferior, identidade visual própria mínima e i18n en/pt com seletor de idioma (REQ-046).
  - _Output_: casca do app, tema, rotas vazias.
  - _Verify_: `npm run build` ok; trocar o idioma altera os textos do menu; em 375px o menu é a barra inferior sem rolagem horizontal.
- [x] **TASK-010** [REQ-001, REQ-002, REQ-003]: Telas Entrar, Criar conta e Recuperar senha, com guarda de rota e armazenamento do token.
  - _Output_: páginas e cliente da API.
  - _Verify_: Vitest dos formulários; fluxo manual cadastro → login → logout.
- [x] **TASK-011** [REQ-005]: Onboarding curto na primeira entrada (`POST /api/me/onboarding-visto` + modal).
  - _Output_: endpoint, modal.
  - _Verify_: aparece no 1º login e não reaparece depois de dispensado.
- [x] **TASK-012** [REQ-004, NFR-004]: Escrever o `CLAUDE.md` definitivo com comandos de build/test e convenções confirmadas (já existe versão inicial).
  - _Output_: `CLAUDE.md` atualizado.
  - _Verify_: comandos documentados executam sem erro.

## Phase 2: Categorias, cartões e pessoas
*Goal*: Usuária mantém seus cadastros por ano, com a regra de exclusão e a cópia para o novo ano.
*Antes de começar*: nada pendente (lista das 15 categorias resolvida em `design.md`).

- [x] **TASK-013** [REQ-010, REQ-011, REQ-013, REQ-014]: Migração V2 com `categoria`, `cartao`, `pessoa` (únicos por usuária/ano/nome).
  - _Output_: `V2__catalog.sql` (tabelas `category`, `card`, `person`).
  - _Verify_: app sobe nos dois perfis; inserir nome repetido no mesmo ano falha. Verificado em 2026-11-01: H2 (44 testes) e MySQL 8.4 (V2 aplicada).
- [x] **TASK-014** [REQ-010]: Semente das categorias de fábrica com descrição, criadas no cadastro e no novo ano.
  - _Output_: serviço de semente e testes.
  - _Verify_: conta nova tem as 15 categorias no ano vigente.
- [x] **TASK-015** [REQ-011, REQ-004]: CRUD de categorias (criar, renomear, descrever, desativar) com testes de isolamento.
  - _Output_: serviço, controller, testes.
  - _Verify_: desativada some da lista de ativas; usuária B recebe 404.
- [x] **TASK-016** [REQ-013, REQ-004]: CRUD de cartões com `dia_vencimento` 1–31.
  - _Output_: serviço, controller, testes.
  - _Verify_: dia 0 e 32 rejeitados; isolamento ok.
- [x] **TASK-017** [REQ-014, REQ-004]: CRUD de pessoas.
  - _Output_: serviço, controller, testes.
  - _Verify_: desativar mantém o registro; isolamento ok.
- [x] **TASK-018** [REQ-012]: Teste primeiro, depois exclusão de categoria: sem lançamentos exclui; com lançamentos devolve 409 com a quantidade, e a categoria continua existindo. (A checagem de lançamentos usa um contrato que a Phase 3 implementa.)
  - _Output_: testes dos 2 cenários do PDF, serviço, `DELETE /api/categorias/{id}`.
  - _Verify_: cenários "Categoria com lançamentos não é excluída direto" e "Categoria sem lançamentos é excluída" passam.
- [x] **TASK-019** [REQ-007, REQ-008]: Criar novo ano copiando categorias, cartões e pessoas (`POST /api/anos/{ano}`), e listar anos.
  - _Output_: serviço, endpoints, testes.
  - _Verify_: editar a lista de 2027 não altera 2026; criar ano existente → erro.
- [x] **TASK-020** [REQ-044, REQ-007]: Telas de Categorias, Cartões, Pessoas e Configurações (com criação de novo ano), incluindo o diálogo de exclusão de categoria.
  - _Output_: 4 páginas.
  - _Verify_: Vitest básico; fluxo manual de criar, desativar e excluir.

## Phase 3: Lançamentos de gastos
*Goal*: Usuária lança fixos e cartão, com parcelas corretas e carga automática ao abrir um mês.
*Antes de começar*: resolver Open Questions de crédito sem cartão, fixo com cartão e padrão de parcelas automáticas.

- [x] **TASK-021** [REQ-020, NFR-003]: **Teste primeiro**, depois `regras.Parcelas.dividirTotal` e `dividirPorValor`.
  - _Output_: testes dos cenários "Compra dividida sem perder centavos" e de parcelas automáticas; classe pura.
  - _Verify_: 3333/3333/3334; 4×5000; propriedade: soma das parcelas = total para totais e n variados.
- [x] **TASK-022** [REQ-021, NFR-003]: Teste e implementação do indicador "N/M".
  - _Output_: `regras.IndicadorParcela`.
  - _Verify_: 7 de 12 → "7/12"; sem parcelas → vazio.
- [~] **TASK-023** [REQ-015, REQ-016, REQ-017, REQ-019]: Migração V3 com `lancamento` e entidade/repositório; criação de lançamento único (fixo e cartão, terceiro, parcela manual).
  - _Output_: `V3__lancamentos.sql`, `POST /api/meses/{mes}/lancamentos`.
  - _Verify_: testes de criação; `data` fora do `mes` rejeitada; `valor_centavos ≤ 0` rejeitado. Aplicar as "Regras de consistência do lançamento" do `design.md` (fixo no crédito permitido; cartão só em crédito; parcelas só em tipo cartão). **Pendente: aplicar V3 no MySQL (validação do Hibernate); H2 verde (162 testes).**
- [x] **TASK-024** [REQ-018, REQ-009, REQ-020]: Geração de parcelas automáticas (`gerarParcelas`) respeitando a permissão e usando `grupo_parcela_id`.
  - _Output_: serviço e testes.
  - _Verify_: cenários "Parcelas automáticas" do PDF (inclui permissão desligada e sem parcelas).
- [x] **TASK-025** [REQ-023, REQ-015, REQ-033]: Editar, excluir e marcar como pago (`PUT`, `DELETE`, `PATCH .../pago`).
  - _Output_: endpoints e testes.
  - _Verify_: marcar pago não muda `valor_centavos`; isolamento ok.
- [x] **TASK-026** [REQ-022, NFR-003]: **Teste primeiro**, depois `regras.CargaDoMes` (puro): fixos sem "pago", próxima parcela, última parcela não se repete, parcelas já geradas não se repetem.
  - _Output_: testes dos 3+1 cenários "Carga ao abrir um mês" e "Parcelas geradas não se repetem".
  - _Verify_: todos verdes.
- [x] **TASK-027** [REQ-022]: Migração `mes_carregado` e serviço que aplica `CargaDoMes` na primeira abertura do mês (idempotente), incluindo a virada dez→jan.
  - _Output_: migração, serviço.
  - _Verify_: abrir o mês 2× não duplica; dezembro → janeiro abre o novo ano e copia estrutura. Verificado no H2 (`MonthLoadServiceTest`); **a V4 também precisa ser aplicada no MySQL** (junto da V3, TASK-023). O serviço `MonthLoadService.open` ainda não é chamado por nenhum endpoint: a TASK-034 (`GET /api/months/{mes}`) vai chamá-lo.
- [x] **TASK-028** [REQ-023, REQ-004]: Testes de isolamento de todos os endpoints de lançamento.
  - _Output_: testes.
  - _Verify_: usuária B recebe 404 em PUT/DELETE/PATCH de lançamento de A. Coberto em `ExpenseCreateApiTest` (POST com categoria/cartão/pessoa alheios → 404, `userId` no corpo ignorado) e `ExpenseEditApiTest` (PUT/PATCH/DELETE → 404 e nada muda); o `GET` do mês entra na TASK-034.
- [x] **TASK-029** [REQ-016, REQ-017, REQ-018, REQ-019, REQ-042]: Janela "Novo lançamento" no front (nome, valor, categoria, tipo, cartão, pessoa, data, parcelas automáticas ou mês a mês, salvar e lançar outro).
  - _Output_: componente de formulário e integração.
  - _Verify_: Vitest do formulário; lançar 3 gastos seguidos sem fechar a janela.

## Phase 3.5: CI/CD e segurança
*Goal*: Pipeline no GitHub Actions que testa, verifica segurança e publica (Cloudflare Pages, Render, Aiven) só o que mudou. Pedido da usuária em 2026-11-01, antes da Phase 4 (design.md 0.2.0, "CI/CD", D-13 a D-18).

- [x] **TASK-051** [REQ-047]: **Teste primeiro**, depois `security.AttemptLimiter` nos quatro `POST /api/auth/**` (429 `TOO_MANY_ATTEMPTS` + `Retry-After`); front traduz o código.
  - _Verify_: `RateLimitApiTest` (6ª falha bloqueada, e-mail existente ou não, sucesso zera) e Vitest.
- [x] **TASK-052** [NFR-008, REQ-003]: `DisabledEmailSender` no perfil `prod`; testes que capturam o log e as respostas de cadastro, login e recuperação.
  - _Verify_: nenhuma senha, JWT ou token de recuperação no log capturado nem em resposta.
- [x] **TASK-053** [NFR-009]: perfil `prod` (`application-prod.yml`, Flyway desligado, `PORT`, pool 5, forward headers), `DatabaseTlsGuard`, Actuator só com `/actuator/health`, `flyway-maven-plugin`.
  - _Verify_: teste do guard (URL sem `sslMode` seguro falha); `GET /actuator/health` sem token → 200.
- [x] **TASK-054** [NFR-004, NFR-005, REQ-002]: Testes de segurança: JWT `alg: none`/expirado/adulterado/emissor errado → 401; segredo curto impede o boot; e-mail inexistente e senha errada dão a mesma resposta; BCrypt com custo ≥ 10; regras ArchUnit (DTO sem `userId`, repositórios com `userId`) e `IsolationCoverageTest`.
  - _Verify_: `./mvnw test` verde.
- [x] **TASK-055** [NFR-010]: Cobertura: JaCoCo (`coverage.min` global e `coverage.rules.min` em `rules`, padrão 0.80) e Vitest (`COVERAGE_MIN`, padrão 80).
  - _Verify_: `./mvnw verify` e `npm test -- --coverage` passam no limiar.
- [x] **TASK-056** [NFR-009, D-13]: `backend/Dockerfile` multi-stage (JDK 25 → JRE, não-root, digest fixo, `HEALTHCHECK`, JVM para 512 MB) e `.dockerignore`.
  - _Verify_: `hadolint` sem erros.
- [x] **TASK-057** [D-15, REQ-043]: `_redirects`, `_headers` (CSP sem `unsafe-inline` em scripts, `connect-src 'self'`), Pages Function de proxy e E2E Playwright (cadastro/login → lançamento; saldo entra após a TASK-034).
  - _Verify_: E2E local contra o back no perfil `h2`.
- [x] **TASK-058** [NFR-010]: Workflows (`ci-cd`, `backend-deploy`, `frontend-deploy`, `integration-tests`, `security`), `dependabot.yml`, `.semgrep/`, `.gitleaks.toml`, `.zap/rules.tsv`, `scripts/ci/`; actions fixadas por SHA.
  - _Verify_: `actionlint` sem erros; scripts SQL/shell rodados contra o MySQL local quando possível.
- [x] **TASK-059** [NFR-010]: `.env.example` e `docs/ci-cd.md` (fluxo, segredos, checklist Cloudflare/Render/Aiven, rollback, rotação, cold start, achados).
  - _Verify_: revisão da usuária (OK em 2026-11-04).
- [x] **TASK-060** [NFR-010]: Validar o pipeline em `ci/pipeline-test` por `workflow_dispatch` depois de cadastrar os segredos (precisa da usuária: contas e push).
  - _Verify_: todos os jobs verdes até o preview do front (OK no PR #1 em 2026-11-02); deploy de produção só após aprovação (OK em 2026-11-04: Backend run 41752594996 — Flyway V1–V4 na Aiven, Render `dep-dbl9c10n74is739ek0t0`, smoke; Frontend run 41749872952 em https://nize.pages.dev com `/api` via proxy).

---

## Phase 4: Entradas e saldo do mês
*Goal*: Mês mostra total de gastos, fixos, saldo, posição dia 15/30, reembolsos e saldo próprio.

- [x] **TASK-030** [REQ-027, REQ-028, REQ-029, NFR-003]: **Teste primeiro**, depois `regras.TotaisDoMes` (gastos, fixos, saldo).
  - _Output_: testes dos cenários "Totais do mês".
  - _Verify_: 99399+494889=594288; saldo 68070.
- [x] **TASK-031** [REQ-030, REQ-031, REQ-032, REQ-033, REQ-048, NFR-003]: **Teste primeiro**, depois `rules.ClosingPositions` com `dataEfetiva` (Pix pela data; crédito pelo vencimento; dias de fechamento quaisquer; dia além do fim do mês = último dia).
  - _Output_: testes dos cenários "Posição por quinzena" e "Pix e crédito" (com os dias padrão 15 e 31) e dos fechamentos configuráveis.
  - _Verify_: sobra 180000; −30000; Pix 12/04 até o 15; crédito com vencimento 10 até o 15; "pago" não altera; dias 5/10/20 → três posições; 30 e 31 em fevereiro → uma posição no 28/29; sem dias → nenhuma.
- [x] **TASK-032** [REQ-034, NFR-003]: **Teste primeiro**, depois `regras.SaldoProprio`.
  - _Output_: teste e classe.
  - _Verify_: 50000, 20000, 19000 → 51000.
- [x] **TASK-033** [REQ-024, REQ-025, REQ-026]: Migração V5 com `entrada` (`income`); criação, edição e exclusão de entradas, parceladas e reembolso; estender `CargaDoMes` às entradas parceladas.
  - _Output_: migração, serviço, endpoints, testes.
  - _Verify_: entrada 1/3 gera 2/3 e 3/3 nos meses seguintes sem duplicar; entrada com `pessoa_id` conta como reembolso; isolamento ok.
- [x] **TASK-061** [REQ-048, REQ-044]: Migração V6 (`settings.closings_enabled`, `closing_day` com 15 e 31 para as contas existentes); cadastro grava 15 e 31; `GET/PUT /api/settings` com `closingsEnabled`/`closingDays`; tela Configurações com ligar/desligar e lista de dias.
  - _Output_: migração, serviço, testes de API e de isolamento, tela e testes Vitest.
  - _Verify_: conta nova tem 15 e 31; dia 0, 32 ou repetido → 400; outra usuária não vê nem altera; desligar mantém os dias salvos.
- [x] **TASK-034** [REQ-027..REQ-034, REQ-048]: Montar o bloco de totais e posições em `GET /api/meses/{mes}` a partir das regras.
  - _Output_: DTO do cabeçalho e testes de integração.
  - _Verify_: resposta bate com os exemplos do PDF; nenhuma coluna de total no banco (NFR-002).

## Phase 5: Resumos
*Goal*: Resumos por categoria, forma de pagamento, cartão e pessoa que sempre fecham com os totais.

- [ ] **TASK-035** [REQ-035, NFR-003]: **Teste primeiro**, depois `regras.ResumoPorCategoria` com porcentagem.
  - _Output_: testes e classe.
  - _Verify_: 141814/594288 = 23,86%; Σ categorias = total de gastos.
- [ ] **TASK-036** [REQ-036, REQ-037, NFR-003]: **Teste primeiro**, depois resumos por forma de pagamento e por cartão com "Sem cartão".
  - _Output_: testes e classes.
  - _Verify_: Σ por cartão (com "Sem cartão") = total de crédito.
- [ ] **TASK-037** [REQ-038, NFR-003]: **Teste primeiro**, depois resumo de terceiros por pessoa e por cartão.
  - _Output_: testes e classe.
  - _Verify_: Σ por pessoa = total de gastos de terceiros.
- [ ] **TASK-038** [REQ-035..REQ-038]: Incluir os resumos em `GET /api/meses/{mes}`.
  - _Output_: DTOs e testes de integração.
  - _Verify_: invariantes de soma verificadas em teste com dados mistos.

## Phase 6: Tela do mês
*Goal*: A tela principal junta tudo, com navegação entre meses e lançamento rápido.

- [ ] **TASK-039** [REQ-041, REQ-040]: Página `/mes/:aaaa-mm` com cabeçalho (totais e posições), seletor de mês/ano e carga ao abrir.
  - _Output_: página e hooks.
  - _Verify_: trocar de mês atualiza os números; valores exibidos vêm só da API.
- [ ] **TASK-040** [REQ-041, REQ-015, REQ-016, REQ-024, REQ-023]: Listas de Fixos, Cartão e Entradas com "pago", editar e excluir, e indicador N/M.
  - _Output_: componentes de lista.
  - _Verify_: marcar pago não altera os totais exibidos; editar e excluir refletem na hora.
- [ ] **TASK-041** [REQ-041, REQ-035..REQ-038, REQ-034]: Painel de resumos (categoria, forma, cartão, pessoa) e saldo próprio.
  - _Output_: componentes do painel.
  - _Verify_: números idênticos aos da API; sem cálculo no front.
- [ ] **TASK-042** [REQ-042, REQ-024, REQ-026]: Botão de lançamento rápido na tela do mês para gasto e entrada (reuso da janela da TASK-029; entrada com parcelas e reembolso).
  - _Output_: janela de entrada.
  - _Verify_: lançar um gasto em poucos segundos sem sair da tela.
- [ ] **TASK-043** [REQ-043, NFR-006]: Ajustar a tela do mês para 360px (listas empilhadas, painel recolhível).
  - _Output_: ajustes de CSS.
  - _Verify_: sem rolagem horizontal em 360px, 768px e 1280px.

## Phase 7: Panorama anual
*Goal*: Visão dos 12 meses coerente com a tela do mês.

- [ ] **TASK-044** [REQ-039, NFR-002]: `GET /api/anos/{ano}/panorama` calculando gastos, entradas e saldo dos 12 meses (sem persistir totais).
  - _Output_: serviço, DTO, testes.
  - _Verify_: cada mês bate com `GET /api/meses/{mes}`.
- [ ] **TASK-045** [REQ-039, REQ-040]: Tela do panorama com gráfico, tabela de saldo mensal e clique que abre o mês.
  - _Output_: página `/panorama/:ano`.
  - _Verify_: clicar em março abre `/mes/AAAA-03`; trocar de ano funciona.

## Phase 8: Validação final do MVP
*Goal*: Garantir que o MVP cumpre a spec antes do primeiro deploy de teste.

- [ ] **TASK-046** [NFR-003]: Conferir que todos os cenários do PDF existem como testes (tabela cenário → teste).
  - _Output_: seção no final deste arquivo ou `docs/cobertura-cenarios.md`.
  - _Verify_: nenhum cenário sem teste; `mvn test` verde.
- [ ] **TASK-047** [NFR-004, REQ-004]: Rodar a suíte de isolamento sobre todos os endpoints de dados.
  - _Output_: relatório de endpoints × teste de isolamento.
  - _Verify_: 100% dos endpoints de dados cobertos.
- [ ] **TASK-048** [NFR-007]: Teste de carga simples do resumo do mês com 500 lançamentos.
  - _Output_: resultado registrado.
  - _Verify_: p95 < 500 ms (ou meta revisada).
- [ ] **TASK-049** [REQ-045]: Revisão de identidade visual: nada copiado da planilha.
  - _Output_: checklist assinado.
  - _Verify_: revisão manual.
- [ ] **TASK-050** [NFR-005, NFR-006]: Subir o MVP na hospedagem de teste (D-13) usando o pipeline da Phase 3.5.
  - _Output_: URLs de teste; Dockerfile e `docs/ci-cd.md` já vêm da Phase 3.5.
  - _Verify_: cadastro → lançamento → resumo funcionando na URL de teste.

---

## Completed Tasks Archive
<!-- Move [x] tasks here at end of each sprint to keep active list clean -->
