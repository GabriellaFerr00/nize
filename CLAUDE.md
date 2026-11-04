# CLAUDE.md
> Nize — Spec version 0.2.4

═══════════════════════════════════════════════════════════
SPEC DRIVEN DEVELOPMENT — PROJECT CONSTITUTION
Project: Nize
Version: 0.2.4
═══════════════════════════════════════════════════════════

This project uses Spec Driven Development. All work is
governed by three source-of-truth files:

  requirements.md  — What the system must do
  design.md        — How the system is structured
  tasks.md         — The ordered implementation plan

MANDATORY BEFORE ANY ACTION:
  0. If CONTEXT.md exists, read it first — it has session state
  1. Read requirements.md in full
  2. Read design.md in full
  3. Read tasks.md — identify the next incomplete [ ] task

HARD CONSTRAINTS:
  ✗ Never implement requirements not in requirements.md
  ✗ Never alter the data model without updating design.md first
  ✗ Never create files not listed or implied in design.md
  ✗ Never mark a task [x] without verifying its acceptance criterion
  ✗ Never guess when a requirement is ambiguous — ask instead

AFTER COMPLETING A TASK:
  1. Run the verification step listed in tasks.md
  2. Mark the task [x] in tasks.md
  3. Report what was done and which REQ/NFR it satisfies

DIVERGENCE PROTOCOL:
  If implementation must deviate from design.md:
    → Stop immediately
    → Describe the conflict clearly
    → Wait for explicit user approval
    → Update design.md BEFORE writing code
═══════════════════════════════════════════════════════════

## Claude Code — regras específicas
- Fonte original do produto: `Nize — Funcionalidades e escopo do MVP web.pdf` (Downloads). Spec e PDF divergem → perguntar.
- Antes de iniciar uma fase de `tasks.md`, resolver as Open Questions de `design.md` citadas nela.
- Ao fechar uma tarefa, perguntar se a implementação divergiu de `design.md`; se sim, atualizar `design.md` primeiro.
- Atualizar `CONTEXT.md` ao fim de cada sessão de trabalho.

## Projeto
- **O que é**: app web de finanças pessoais mensais (gastos fixos e de cartão, entradas, saldo, posição até o dia 15/30). Não conecta a bancos nem executa Pix.
- **Stack**: back-end Java 25 + Spring Boot 4.0.9 + Maven (`backend/`, via `./mvnw`); front-end React 19 + TypeScript + Vite + Tailwind 4 + TanStack Query + i18next (`frontend/`); banco MySQL 8.4 local (perfil `local`) e H2 em memória (perfil `h2`); Flyway.
- **Deploy**: provisório e gratuito para testes (Render + Vercel/Netlify/Cloudflare Pages).
- **Estrutura**: monorepo; `backend/` e `frontend/` independentes (ver "File Structure" em `design.md`). Pacote Java raiz `br.com.nize` (`rules`, `domain`, `repository`, `service`, `api`, `security`).

## Regras inegociáveis do domínio
- Dinheiro é sempre inteiro em centavos (`long`/`BIGINT`). Nunca `double`, `float` ou `BigDecimal` em valores monetários.
- Mês é texto `AAAA-MM`.
- Saldo, totais e porcentagens nunca são persistidos: são calculados.
- **Toda regra de cálculo fica no back-end, no pacote puro `br.com.nize.rules`** (sem Spring/JPA). O front só exibe e nunca recalcula.
- Regras de cálculo: escrever o teste **antes** do código, usando os cenários do PDF/`requirements.md`.
- Toda tabela tem `user_id`; `user_id` vem do token, nunca do corpo da requisição. Linha de outra usuária → 404.
- "Pago" é só marca de controle e nunca altera valores.
- Não copiar textos, layout ou identidade visual da planilha original.

## Ambiente local (WSL, sem sudo)
Ferramentas ficam em `~/tools`. Em cada shell novo:
```
export JAVA_HOME=~/tools/jdk25 PATH=~/tools/jdk25/bin:$PATH                       # back (o Maven vem do ./mvnw)
export LD_LIBRARY_PATH=~/tools/libs/x/usr/lib/x86_64-linux-gnu PATH=~/tools/node/bin:$PATH   # front (Node + libatomic local)
```
- O segredo do JWT é obrigatório para **rodar** o back (os testes usam um valor fixo do `pom.xml`): `export NIZE_JWT_SECRET=<no mínimo 32 bytes>`. Sem ele o app não sobe.
- MySQL (perfil `local`) fica no host Windows: `NIZE_DB_URL=jdbc:mysql://<gateway do WSL>:3306/nize`, `NIZE_DB_USER`, `NIZE_DB_PASSWORD`. Usuário dedicado `nize`@`172.28.%`; ver `backend/HELP.md`. Nunca gravar senha em arquivo.

## Comandos
Back-end (a partir da raiz):
- Testes: `cd backend && ./mvnw test`
- Rodar com H2: `cd backend && NIZE_JWT_SECRET=<segredo> ./mvnw spring-boot:run -Dspring-boot.run.profiles=h2` (porta 8080)
- Rodar com MySQL: o mesmo com `-Dspring-boot.run.profiles=local` e as variáveis `NIZE_DB_*`.

Front-end (a partir da raiz):
- Dev (proxy `/api` → `localhost:8080`): `npm --prefix frontend run dev`
- Testes: `npm --prefix frontend test` · Build: `npm --prefix frontend run build` · Lint: `npm --prefix frontend run lint`

## Convenções confirmadas
**Back-end**
- Identificadores técnicos em inglês; tabela de nomes português → inglês em `design.md` ("Nomenclatura em inglês"). Mudar um nome exige atualizar essa tabela antes.
- Usuária autenticada: use `CurrentUser.id()` (`security`); DTOs de entrada nunca têm `userId`. Linha de outra usuária → 404 (não 403).
- Erros da API: JSON `{code, fields}` com códigos estáveis em inglês (`EMAIL_ALREADY_REGISTERED`, `INVALID_CREDENTIALS`, `INVALID_RESET_TOKEN`, `UNKNOWN_YEAR`, `VALIDATION_ERROR`, `TOO_MANY_ATTEMPTS`); o front traduz. Todos tratados em `ApiExceptionHandler`, inclusive os do framework (404 `NOT_FOUND`, 405 `METHOD_NOT_ALLOWED`, 415 `UNSUPPORTED_MEDIA_TYPE`, 500 `INTERNAL_ERROR`, sem detalhes no corpo).
- Senha: 8–72 caracteres, BCrypt; nunca logada nem devolvida. Login e recuperação não revelam se o e-mail existe.
- `/api/auth/**` tem limite de tentativas (`AttemptLimiter`, REQ-047). Nos testes, o Surefire sobe os limites; teste de limite usa `@SpringBootTest(properties=...)` com limites baixos e IPs/e-mails próprios.
- Perfil `prod` (Render): Flyway desligado (migração é passo do CI com outro usuário), TLS obrigatório na URL (`DatabaseTlsGuard`), `DisabledEmailSender`. Só `/actuator/health` é público.
- Cadastros por ano seguem o molde `Category*`/`Card*`/`Person*`: repositório com `userId` em toda consulta, `YearGuard` para o ano, `ResourceNotFoundException` → 404, nome único ignorando maiúsculas por ano (409 `*_NAME_TAKEN`), `PUT` com `active` obrigatório, `?active=true` na listagem.
- Lançamentos: `ExpenseService` valida as "Regras de consistência do lançamento" (design.md); referências inativas só são aceitas se o lançamento já as usava; `month` é reservada no H2 (coluna `month_ref`). `ddl-auto: validate` exige `@JdbcTypeCode(CHAR/VARCHAR/SMALLINT)` nas colunas que não são o padrão do Hibernate (e enum como `VARCHAR`).
- Dinheiro em JSON: `spring.jackson.deserialization.accept-float-as-int=false` (10.5 é recusado, não vira 10). No front: `parseCents`/`formatCents` em `src/lib/money.ts`, sem float.
- Em testes, um método auxiliar chamado `post`/`put` esconde o import estático de `MockMvcRequestBuilders`: dê outro nome (`create`, `update`, `generate`).
- Teste de isolamento de todo endpoint de dados: estender `IsolationTestBase` (`as(user, request)`, `assertOnlyOwnerCanAccess`).
- `ArchitectureTest` (ArchUnit): campo monetário (nome com `amount`, `cents`, `valor`, `total`, `balance`...) nunca `double`/`float`/`BigDecimal`; `rules` sem Spring/JPA. Se criar um campo monetário com nome novo, acrescente-o à regex `MONEY_NAME`.
- Regras de cálculo: teste primeiro (cenários do PDF), classe pura em `br.com.nize.rules`.
- Boot 4: `AutoConfigureMockMvc` está em `org.springframework.boot.webmvc.test.autoconfigure`; starters de teste são `*-webmvc-test`, `*-security-test`. `year` é palavra reservada no H2 (usar `year_number`).
- Testes de API: `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("h2") @Transactional`; `JdbcTemplate` não vê o que o JPA não deu flush (chamar `entityManager.flush()` antes).

**Front-end**
- O front só exibe o que a API devolve; nenhum cálculo de dinheiro, saldo ou porcentagem.
- Todo texto visível vem de `src/i18n/{pt,en}.json` (padrão `pt`); chaves iguais nos dois arquivos. Erros da API: `apiErrorMessage` por código.
- Chamadas à API só por `src/api/client.ts` (`apiRequest`); sessão em `src/api/session.ts` (`localStorage`, D-12). Cache de consultas é limpo ao entrar, sair e em 401.
- Rotas do front em inglês (`/month/:yyyyMm`, `/overview/:year`, `/sign-in`...). Rotas protegidas ficam sob `RequireAuth`.
- Tema e cores só como tokens em `src/styles/index.css` (`@theme`); sem cores soltas. Layout: menu lateral ≥ 768px, barra inferior abaixo; sem rolagem horizontal.
- Testes: `renderApp(path, {signedIn})` e `stubApi({...})` em `src/test/renderApp.tsx` (o objeto de respostas é lido a cada chamada: altere-o para simular o refetch).
- Cadastros por ano (categorias, cartões, pessoas): reutilizar `CatalogManager` + `catalogApi`; ano da tela via `useSelectedYear` (`?year=`, padrão = ano vigente).

**CI/CD** (`docs/ci-cd.md`)
- Workflows em `.github/workflows/`; actions fixadas por SHA e scanners por digest. Validar com `docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:1.7.12` antes de subir.
- Cobertura mínima 80% (JaCoCo `coverage.min`/`coverage.rules.min`; Vitest `COVERAGE_MIN`). E2E: `npm --prefix frontend run e2e` com o back em :8080.
- Arquivo novo de pipeline/script também precisa entrar em "File Structure" do `design.md`.

**Armadilhas deste ambiente**
- Nunca usar `pkill -f`/`pgrep -f` com texto que apareça no próprio comando (derruba o shell): encerrar servidores por PID ou porta.
- Resposta HTTP 204 em teste não pode ter corpo (nem string vazia).
- `open-in-view: false` + testes `@Transactional` escondem `LazyInitializationException` (a sessão fica aberta no teste). Coleção lida pela API depois do serviço: `fetch = EAGER` ou carregar dentro da transação; ver `SettingsOutsideTransactionTest`.
