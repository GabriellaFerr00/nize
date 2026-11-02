# CONTEXT.md
> Nize — Live session journal
> Last updated: 2026-11-02 — Session 3

## ⚡ Resume from here

**Active task:** Phase 3.5 (CI/CD e segurança) implementada localmente, SEM commit/push. Falta: (1) a usuária revisar `docs/ci-cd.md` (TASK-059); (2) ela criar Environments/secrets/contas (Aiven, Cloudflare, Render) seguindo `docs/ci-cd.md` §5 ANTES do primeiro push dos workflows; (3) TASK-060: validar em `ci/pipeline-test` via workflow_dispatch. Depois: aplicar V3/V4 no MySQL local (TASK-023) e Phase 4 (TASK-030).
**Phase:** Phase 3.5 — TASK-051..058 [x], 059 [~], 060 [ ]. Back 240+ testes, cobertura 96% (rules 100%); front 86 testes, 96% linhas. actionlint, hadolint, shellcheck, trivy (imagem e Dockerfile), gitleaks e semgrep limpos localmente.
**Status:** Nada commitado nesta sessão. `CICD_PROMPT.md` (pedido da usuária) segue não rastreado.
**Blocker (if any):** contas e segredos externos só a usuária pode criar.
Para rodar o app é preciso `export NIZE_JWT_SECRET=<>=32 bytes>` (os testes usam um valor fixo no pom). Repositório: https://github.com/GabriellaFerr00/nize (público, branch `main`; autor local `Gabriella <86236510+GabriellaFerr00@users.noreply.github.com>`; `gh` em `~/tools/gh/bin`, credencial só neste repo). Para scripts que sobem servidores, NÃO usar `pkill -f` com texto que apareça no próprio comando (derruba o shell); usar PID/porta. Toolchain: `export JAVA_HOME=~/tools/jdk25 PATH=~/tools/jdk25/bin:$PATH`; usar `cd backend && ./mvnw test` (Maven Wrapper). Docker funciona no WSL (sem buildx; scanners rodam por imagem). Não há `jq` local.

> On session start: read this file first, then requirements.md,
> design.md, and tasks.md. Do not ask what to work on — resume
> the active task above unless the user says otherwise.

---

## Session log

| # | Date | What was done | Files changed |
|---|---|---|---|
| 3 | 2026-11-02 | Phase 3.5 (CI/CD, pedido em `CICD_PROMPT.md`): specs 0.2.0 (requirements 0.1.4: REQ-047, NFR-008..010; design D-13..D-18; tasks Phase 3.5). Back: `AttemptLimiter` (429 TOO_MANY_ATTEMPTS), perfil `prod` (Flyway off, pool 5, forward headers), `DatabaseTlsGuard` (BeanFactoryPostProcessor), `DisabledEmailSender`, Actuator health, JWT exige `exp`, `ApiExceptionHandler` cobre erros do framework (404/405/415/500 com `{code, fields}`), JaCoCo 80% e flyway-maven-plugin; testes AuthHardening, SecretsNotExposed, JwtSecretBoot, DatabaseTlsGuard, ErrorContract, RateLimit, IsolationCoverage, ArchUnit (DTO sem userId, repos com userId, sem findById puro). `backend/Dockerfile`. Front: `_redirects`, `_headers`, Pages Function proxy `/api`, Vitest coverage 80%, Playwright E2E. Workflows (ci-cd, backend-deploy, frontend-deploy, integration-tests, security), dependabot, `.semgrep/`, `.gitleaks.toml`, `.zap/`, `scripts/ci/`, `.env.example`, `docs/ci-cd.md` | backend/**, frontend/**, .github/**, scripts/ci/**, docs/ci-cd.md, specs |
| 2 | 2026-11-01 | TASK-025: `PUT/PATCH(/paid)/DELETE /api/expenses/{id}`. TASK-026: regra pura `MonthLoad.decide`. TASK-027: V4 `loaded_month` + `MonthLoadService.open` (idempotente; Dez→Jan cria o ano e mapeia refs por nome; item sem categoria no ano novo é ignorado). TASK-028: isolamento coberto pelos testes de lançamento. TASK-029: `ExpenseFormDialog` (+ `lib/money`), botão na `MonthPage`. TASK-021/022: `Installments.splitTotal/repeatValue`, `InstallmentIndicator.format`. TASK-023: V3 `expense`, `POST /api/months/{m}/expenses` (regras de consistência, 400 INVALID_EXPENSE/INVALID_MONTH/DATE_OUT_OF_MONTH/INACTIVE_REFERENCE), `ExpenseCategoryUsage` real; Jackson rejeita decimal em centavos. TASK-024: geração de parcelas (`generateInstallments`, `amountIsTotal`, `createdCount`; refs do ano seguinte por nome; ano criado se faltar; 400 REFERENCE_UNAVAILABLE). TASK-020: telas Categorias/Cartões/Pessoas (`CatalogManager` genérico) e Configurações (ano vigente, parcelas automáticas, novo ano); diálogo de exclusão com contagem de lançamentos. Phase 2 fechada e enviada ao GitHub. TASK-019: `GET /api/years`, `POST /api/years/{year}` (copia categorias/cartões/pessoas do ano anterior mais próximo, preserva `active`; sem ano anterior semeia as de fábrica; 409 YEAR_ALREADY_EXISTS; 400 INVALID_YEAR fora de 2000–2100). TASK-017: CRUD de pessoas (`/api/years/{year}/people`, `/api/people/{id}`, PERSON_NAME_TAKEN). TASK-018: `DELETE /api/categories/{id}` (204; 409 CATEGORY_IN_USE com `count`; contrato `CategoryUsage` + placeholder `NoExpensesYet`). TASK-016: `GET/POST /api/years/{year}/cards` (`?active=true`), `PUT /api/cards/{id}`; `dueDay` 1–31 validado na API e no banco; 409 CARD_NAME_TAKEN; `YearGuard` extraído (usado por Category e Card). TASK-015: `GET/POST /api/years/{year}/categories` (`?active=true`), `PUT /api/categories/{id}`; 404 NOT_FOUND p/ ano ou linha de outra usuária; 409 CATEGORY_NAME_TAKEN (ignora maiúsculas); DELETE fica p/ TASK-018. TASK-014: `Category`, `CategoryRepository`, `DefaultCategories` (15, texto próprio), `CategorySeeder` (idempotente), seed no cadastro; `DefaultCategoriesSyncTest` compara código x design.md (0.1.6). TASK-013 fechada (V2 também no MySQL). TASK-013 (H2): V2__catalog.sql + CatalogSchemaTest. Repositório GitHub criado e Phase 1 enviada (commit inicial). TASK-012: CLAUDE.md definitivo (ambiente, comandos, convenções confirmadas, armadilhas) e verificado. Phase 1 fechada. TASK-011: `POST /api/me/onboarding-seen` (204, idempotente), `OnboardingModal` (GET /api/me; Esc/botão dispensam), cache de consultas limpo em entrar/sair/401 (evita vazar dados entre usuárias no mesmo navegador). TASK-003 fechada: V1 aplicada no MySQL 8.4 (usuário `nize`@`172.28.%`, só no banco `nize`; root remoto não é aceito). TASK-010: telas /sign-in, /sign-up, /forgot-password, /reset-password; `api/` (client, session, authApi, AuthContext), guardas RequireAuth/PublicOnly (volta à página pedida), botão Sair, mensagens traduzidas por código de erro. design.md 0.1.5. TASK-009: `frontend/` (Vite 8, React 19, TS, Tailwind 4, Router, TanStack Query, i18next pt/en padrão pt, lucide-react), tema teal, AppLayout lateral/inferior, 6 páginas vazias. TASK-007/008: `IsolationTestBase` (2 usuárias + `assertOnlyOwnerCanAccess`), `GET /api/me`, `GET/PUT /api/settings` (ano vigente deve existir em `user_year`, senão 400 UNKNOWN_YEAR). TASK-006: `POST /api/auth/forgot-password` (202 sempre) e `/reset-password` (204), token 32 bytes, só SHA-256 no banco, 30 min, uso único, novo pedido invalida os anteriores; `EmailSender` + `LoggingEmailSender` (token no log). TASK-005: `POST /api/auth/login`, JWT HS256 (sub=user id, 1h, issuer nize) via oauth2-resource-server, `CurrentUser`, segredo obrigatório em `NIZE_JWT_SECRET` (falha ao subir sem ele). TASK-004: `POST /api/auth/register` (AppUser/Settings/UserYear, BCrypt, RegistrationService, ApiExceptionHandler com códigos EMAIL_ALREADY_REGISTERED e VALIDATION_ERROR). Maven Wrapper + arquivos padrão Initializr em backend/ (pedido da usuária; design.md 0.1.3). TASK-003 (H2): V1__foundation.sql + FoundationSchemaTest. TASK-002: ArchitectureTest (ArchUnit 1.5.1). TASK-001: backend Maven criado (Spring Boot 4.0.9, Java 25, Flyway, H2/MySQL); `mvn test` verde; app sobe com perfil `h2`. Specs atualizadas p/ 0.1.1 (inglês, REQ-046) | backend/**, ArchitectureTest, archunit.properties, .gitignore, specs, CLAUDE.md |
| 1 | 2026-11-01 | Specs geradas a partir do PDF do MVP | requirements.md, design.md, tasks.md, CLAUDE.md, CONTEXT.md |

*(most recent first — add a row at the start of each session)*

---

## Key decisions

| Decision | Rationale | Session |
|---|---|---|
| Back Java 25 + Spring + Maven; front React + TS + Vite | Escolha da usuária (back) e delegada a Claude (front) | 1 |
| Regras de cálculo só no back; front só exibe | Confirmado pela usuária; back e front não compartilham código | 1 |
| MySQL local, H2 em memória por ora | Só há MySQL local; ambiente de teste temporário | 1 |
| Hospedagem gratuita provisória (Render + front estático) | Sem preferência; só testes | 1 |
| Apenas Claude Code como agente de IA | Informado pela usuária; só `CLAUDE.md` gerado | 1 |
| `year` reservado no H2 → tabela `user_year`, coluna `year_number` | Divergência aprovada pela usuária; design.md 0.1.2 | 2 |
| Senha 8–72 caracteres; e-mail/nome com trim e e-mail em minúsculas; só `spring-security-crypto` até a TASK-005 | Decisões de implementação da TASK-004 (não estavam na spec; confirmar) | 2 |
| JWT: segredo em `NIZE_JWT_SECRET`, 1h de validade, filtro padrão do Spring (oauth2-resource-server) em vez de filtro próprio | Recomendação aceita pela usuária | 2 |
| Token de recuperação vale 30 min; erro único INVALID_RESET_TOKEN (desconhecido/usado/expirado) | Escolha da usuária (30 min) | 2 |
| PUT /api/settings: `currentYear` precisa ser um ano da usuária (`user_year`), senão 400 `UNKNOWN_YEAR`; intervalo 2000–2100 | Decisão de implementação da TASK-008 (spec não dizia; confirmar) | 2 |
| Idioma padrão pt; identidade verde-azulada; rotas do front em inglês (`/month/:yyyyMm`, `/overview/:year`, ...) | Escolhas da usuária / design.md 0.1.4 | 2 |
| Node LTS 26 em `~/tools/node` + libs (`libatomic`, `nss`, `asound`) extraídas de .deb em `~/tools/libs`; Playwright só no scratchpad p/ medir layout | Sem sudo | 2 |
| Token JWT em localStorage (`nize.auth` com expiração); 401 ou expiração limpam a sessão (D-12) | Escolha da usuária | 2 |
| Cadastro faz login automático em seguida | Decisão de UX da TASK-010 (spec não dizia; confirmar) | 2 |
| MySQL local no host Windows (172.28.0.1:3306); app usa usuário dedicado `nize`@`172.28.%` via `NIZE_DB_USER`/`NIZE_DB_PASSWORD` | `root` só aceita conexão local; sub-rede do WSL pode mudar (refazer o GRANT) | 2 |
| Descrições das 15 categorias reescritas com texto próprio; lista em `DefaultCategories` espelha a tabela do design.md (teste de sincronia) | Aprovado pela usuária (REQ-045) | 2 |
| Push ao GitHub só ao fim de cada fase (Phase 1 e 2 enviadas) | Pedido da usuária; confirmar se vale para as próximas fases | 2 |
| Categorias: ano que a usuária não tem → 404; nome repetido (sem distinguir maiúsculas) → 409; PUT é substituição completa (`name`, `description`, `active`) | Decisão de implementação da TASK-015 (spec não detalhava; confirmar) | 2 |
| Exclusão de categoria: conta TODOS os lançamentos que usam a categoria (a categoria já é do ano); 409 devolve `count`; o diálogo "alterar antes?" é do front (TASK-020) | Interpretação do REQ-012 (confirmar) | 2 |
| Novo ano: copia do ano anterior MAIS PRÓXIMO (não necessariamente o imediato); sem anterior, usa as 15 de fábrica; qualquer ano 2000–2100 | Decisão de implementação da TASK-019 (spec só diz "ano anterior"; confirmar) | 2 |
| Carga do mês continua parcelas lançadas à mão (grupo próprio ao criar) e só cria parcelas com a permissão ligada | Escolha da usuária (design 0.1.9, REQ-022) | 2 |
| Fixo pode ser pago no crédito (assinaturas); `month`→`month_ref`, data→`expense_date` | Usuária / H2 (design 0.1.7–0.1.8) | 2 |
| Spring Boot 4.0.9, groupId `br.com.nize` | Escolha da usuária (4.1.2 existe, adotável depois) | 2 |
| Hospedagem: Cloudflare Pages + Render (imagem GHCR por digest, via API) + Aiven MySQL free (D-13); Neon descartado por ser só PostgreSQL | Escolha da usuária, custo zero | 3 |
| Front continua chamando `/api` relativo; Pages Function faz o proxy (D-15) | Usuária: "siga o que tem hoje" | 3 |
| Rate limiting vira REQ-047 (5 falhas/e-mail e 20/IP em 15 min; cadastro 10/IP/h; recuperação 3/e-mail e 10/IP/h; reset 10/IP/h) | Usuária pediu para acrescentar ao requisito; números propostos por Claude (confirmar) | 3 |
| Recuperação de senha desligada em prod (`DisabledEmailSender`) até haver provedor de e-mail | NFR-008; Open Question nova | 3 |
| Cobertura mínima 80% no back e no front | Escolha da usuária | 3 |
| Credenciais de teste geradas por execução no CI; só `NIZE_DB_ROOT_PASSWORD_TEST` é secret | Menos segredos para rotacionar (diverge do prompt) | 3 |
| Tudo em inglês (código, banco, API) + i18n en/pt no front (REQ-046) | Escolha da usuária; tabela de nomes em design.md | 2 |
| Recuperação de senha: token no log atrás de `EmailSender` | Escolha da usuária | 2 |
| JDK/Maven em `~/tools` via tarball | Sem sudo p/ zip/unzip do SDKMAN | 2 |

---

## Open questions

Resolvidas na sessão 2: Spring Boot, groupId, recuperação de senha, idioma. Faltam (design.md): padrão de `parcelas_automaticas`, crédito sem cartão, fixo com cartão, parcela com vencimento diferente, hospedagem, Cucumber vs JUnit, meta NFR-007, padrão de idioma da interface (en ou pt).

---

## Divergences from design.md

Sessão 3: divergências de CI/CD aprovadas (Dockerfile, Flyway desligado em prod com passo próprio, dois usuários de banco); design.md 0.2.0 antes do código. Também: erros do framework passaram a seguir `{code, fields}` (convenção já existente no CLAUDE.md) e o JWT passou a exigir `exp`.

Sessão 2: nomenclatura em inglês (D-10) e i18n (D-11) aprovadas; `year` → `user_year`/`year_number` aprovado. design.md atualizado antes do código (0.1.1, 0.1.2).

---

## Changelog

| Session | Date | Summary |
|---|---|---|
| 3 | 2026-11-02 | Phase 3.5 CI/CD e segurança; specs 0.2.0 |
| 2 | 2026-11-01 | TASK-001 concluída; specs 0.1.1 |
| 1 | 2026-11-01 | Session opened; specs 0.1.0 geradas |
