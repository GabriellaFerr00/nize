# CONTEXT.md
> Nize — Live session journal
> Last updated: 2026-11-01 — Session 2

## ⚡ Resume from here

**Active task:** TASK-013 — Migração V2 com `category`, `card`, `person` (únicas por usuária/ano/nome; coluna `year_number`) — início da Phase 2
**Phase:** Phase 2: Categorias, cartões e pessoas (Phase 1 concluída: TASK-001 a TASK-012)
**Status:** TASK-012 concluída: CLAUDE.md definitivo; todos os comandos documentados rodaram num ambiente limpo (back 38 testes, front 28, build, lint, run h2, dev com proxy, falha sem segredo). Phase 2 não tem Open Questions pendentes (lista das 15 categorias já resolvida).
**Blocker (if any):** nenhum para a TASK-005.
Para rodar o app é preciso `export NIZE_JWT_SECRET=<>=32 bytes>` (os testes usam um valor fixo no pom). Para scripts que sobem servidores, NÃO usar `pkill -f` com texto que apareça no próprio comando (derruba o shell); usar PID/porta. Toolchain: `export JAVA_HOME=~/tools/jdk25 PATH=~/tools/jdk25/bin:$PATH`; usar `cd backend && ./mvnw test` (Maven Wrapper)

> On session start: read this file first, then requirements.md,
> design.md, and tasks.md. Do not ask what to work on — resume
> the active task above unless the user says otherwise.

---

## Session log

| # | Date | What was done | Files changed |
|---|---|---|---|
| 2 | 2026-11-01 | TASK-012: CLAUDE.md definitivo (ambiente, comandos, convenções confirmadas, armadilhas) e verificado. Phase 1 fechada. TASK-011: `POST /api/me/onboarding-seen` (204, idempotente), `OnboardingModal` (GET /api/me; Esc/botão dispensam), cache de consultas limpo em entrar/sair/401 (evita vazar dados entre usuárias no mesmo navegador). TASK-003 fechada: V1 aplicada no MySQL 8.4 (usuário `nize`@`172.28.%`, só no banco `nize`; root remoto não é aceito). TASK-010: telas /sign-in, /sign-up, /forgot-password, /reset-password; `api/` (client, session, authApi, AuthContext), guardas RequireAuth/PublicOnly (volta à página pedida), botão Sair, mensagens traduzidas por código de erro. design.md 0.1.5. TASK-009: `frontend/` (Vite 8, React 19, TS, Tailwind 4, Router, TanStack Query, i18next pt/en padrão pt, lucide-react), tema teal, AppLayout lateral/inferior, 6 páginas vazias. TASK-007/008: `IsolationTestBase` (2 usuárias + `assertOnlyOwnerCanAccess`), `GET /api/me`, `GET/PUT /api/settings` (ano vigente deve existir em `user_year`, senão 400 UNKNOWN_YEAR). TASK-006: `POST /api/auth/forgot-password` (202 sempre) e `/reset-password` (204), token 32 bytes, só SHA-256 no banco, 30 min, uso único, novo pedido invalida os anteriores; `EmailSender` + `LoggingEmailSender` (token no log). TASK-005: `POST /api/auth/login`, JWT HS256 (sub=user id, 1h, issuer nize) via oauth2-resource-server, `CurrentUser`, segredo obrigatório em `NIZE_JWT_SECRET` (falha ao subir sem ele). TASK-004: `POST /api/auth/register` (AppUser/Settings/UserYear, BCrypt, RegistrationService, ApiExceptionHandler com códigos EMAIL_ALREADY_REGISTERED e VALIDATION_ERROR). Maven Wrapper + arquivos padrão Initializr em backend/ (pedido da usuária; design.md 0.1.3). TASK-003 (H2): V1__foundation.sql + FoundationSchemaTest. TASK-002: ArchitectureTest (ArchUnit 1.5.1). TASK-001: backend Maven criado (Spring Boot 4.0.9, Java 25, Flyway, H2/MySQL); `mvn test` verde; app sobe com perfil `h2`. Specs atualizadas p/ 0.1.1 (inglês, REQ-046) | backend/**, ArchitectureTest, archunit.properties, .gitignore, specs, CLAUDE.md |
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
| Spring Boot 4.0.9, groupId `br.com.nize` | Escolha da usuária (4.1.2 existe, adotável depois) | 2 |
| Tudo em inglês (código, banco, API) + i18n en/pt no front (REQ-046) | Escolha da usuária; tabela de nomes em design.md | 2 |
| Recuperação de senha: token no log atrás de `EmailSender` | Escolha da usuária | 2 |
| JDK/Maven em `~/tools` via tarball | Sem sudo p/ zip/unzip do SDKMAN | 2 |

---

## Open questions

Resolvidas na sessão 2: Spring Boot, groupId, recuperação de senha, idioma. Faltam (design.md): padrão de `parcelas_automaticas`, crédito sem cartão, fixo com cartão, parcela com vencimento diferente, hospedagem, Cucumber vs JUnit, meta NFR-007, padrão de idioma da interface (en ou pt).

---

## Divergences from design.md

Sessão 2: nomenclatura em inglês (D-10) e i18n (D-11) aprovadas; `year` → `user_year`/`year_number` aprovado. design.md atualizado antes do código (0.1.1, 0.1.2).

---

## Changelog

| Session | Date | Summary |
|---|---|---|
| 2 | 2026-11-01 | TASK-001 concluída; specs 0.1.1 |
| 1 | 2026-11-01 | Session opened; specs 0.1.0 geradas |
