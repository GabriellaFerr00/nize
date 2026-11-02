# CI/CD e segurança do Nize

Pipeline no GitHub Actions (design.md "CI/CD", D-13 a D-18; NFR-010). Hospedagem de teste, gratuita:
**Cloudflare Pages** (front), **Render** com imagem Docker do **GHCR** (back) e **Aiven for MySQL** (banco).

```
push na main / Pull Request para a main / workflow_dispatch(target)
        │
   changes (dorny/paths-filter) ── .github/workflows/** e scripts/ci/** disparam os dois
        │
 ┌──────┴────────────────────────────────┐
 ▼ Backend (backend-deploy.yml)           ▼ Frontend (frontend-deploy.yml) — espera o back se os dois mudaram
 build: mvnw verify (H2, ArchUnit,         build: npm ci, lint, typecheck, Vitest ≥ 80%, build
        JaCoCo ≥ 80% global e rules)         │
   ├─ integration-tests.yml ─┐              ├─ integration-tests.yml ─┐
   └─ security.yml (backend) ┘              └─ security.yml (frontend)┘
        ▼                                         ▼
 image: docker build → trivy config/image  deploy: main → production (aprovação)
        → push GHCR por digest (só main)           outra branch → preview (staging)
        ▼                                         ▼
 deploy (Environment production, aprovação) smoke: headers, /sign-in → index.html, /api via proxy
   Flyway migrate (usuário de migração, TLS) → falhou? para aqui
   Render API: deploy do digest, espera "live"
   smoke: /actuator/health com espera de cold start + contrato {code, fields}

 security.yml também roda em PR (caminhos sensíveis), toda segunda 06:17 UTC e manualmente.
```

## 1. Detecção e ordem

- `push` na `main`: compara com o commit anterior. **Pull Request para a `main`**: compara com a base do PR. `workflow_dispatch` em outra branch: compara com a `main`.
- Em Pull Request: roda tudo menos produção (imagem sem push, sem migração, sem deploy do back) e publica o **preview** do front em `staging`. PR de fork: só build e segurança, sem segredos.
- O botão *Run workflow* (`workflow_dispatch`) só existe depois que o workflow está na `main`; antes disso, valide por Pull Request.
- Disparo manual: Actions → **CI/CD** → *Run workflow* → escolha a branch e `target`:
  `auto` (detecta), `backend`, `frontend` ou `both`. Na própria `main`, `auto` não acha diferença: use `backend`/`frontend`/`both`.
- Em branch que não é a `main`: o back roda tudo até a imagem (sem push, sem migração, sem deploy); o front publica um **preview** no Environment `staging`.
- `concurrency` por branch, sem cancelar: uma migração ou deploy nunca é interrompido no meio.
- Quando back e front mudam juntos, a integração roda duas vezes (uma por pipeline). Custa só tempo de runner, e manter os pipelines independentes é mais simples.

## 2. Testes antes de qualquer deploy

| Etapa | Onde | O que prova |
|---|---|---|
| `./mvnw verify` | build do back | testes de regras, API (H2), isolamento (`IsolationTestBase`), ArchUnit, segurança; cobertura JaCoCo ≥ `COVERAGE_MIN` (global) e `COVERAGE_RULES_MIN` (`br.com.nize.rules`) |
| `npm run test:coverage` | build do front | Vitest com cobertura ≥ `COVERAGE_MIN` (linhas, branches, funções) |
| `mysql-e2e` | integration-tests.yml | MySQL 8.4 real; Flyway com o usuário de migração (duas vezes, idempotente); regras do esquema; usuário da aplicação **sem DDL**; app no perfil `prod` (Flyway desligado, TLS); smoke; **Playwright** (cadastro → lançamento → login); log sem segredos |
| `api-h2` | integration-tests.yml (manual) | a mesma suíte de API do build, para rodar isolada |

Só dados sintéticos (`*@example.test`, senhas geradas por execução). Nada roda contra produção.

## 3. Portão de segurança (`security.yml`)

| Job | Ferramenta | Bloqueia | Vai para a issue |
|---|---|---|---|
| secrets-and-data | gitleaks + trufflehog (histórico inteiro), `check_pii.py` (CPF, cartão com Luhn), `check-forbidden-files.sh` | qualquer achado | — |
| semgrep | OWASP Top 10, Java, TS, React, JWT, secrets + `.semgrep/nize.yml` | ERROR/HIGH | WARNING/INFO |
| codeql | `java-kotlin` (build manual Maven) e `javascript-typescript`, `security-extended` | security-severity ≥ 7.0 | < 7.0 |
| deps-backend | trivy fs (`pom.xml`) | CRITICAL/HIGH com correção | MEDIUM/LOW |
| deps-frontend | `npm audit` | high/critical | moderate/low |
| frontend-headers | checagem estática de `_headers`/`_redirects` | CSP fraca, header faltando | — |
| dast | `dast-checks.sh` + ZAP baseline autenticado, contra o back local (perfil `h2`) | regra FAIL do `.zap/rules.tsv`, qualquer checagem DAST | avisos do ZAP |
| db-security | MySQL descartável: migração do zero e de novo, esquema, privilégios | qualquer falha | — |
| security-tests | `./mvnw test` | qualquer falha | — |
| image (no pipeline do back) | trivy config + image | CRITICAL/HIGH com correção | (SARIF no Code Scanning) |

- Resultados SARIF ficam em **Security → Code scanning**, por categoria (`gitleaks`, `semgrep`, `codeql-*`, `trivy-maven`, `trivy-image`).
- Médios e baixos viram um comentário na issue aberta **"Security findings (medium/low)"** (criada se não existir). Revise semanalmente.
- Falso positivo: semgrep → `// nosemgrep: <rule-id>` com justificativa; gitleaks → `allowlist` em `.gitleaks.toml`; ZAP → mude a regra em `.zap/rules.tsv`. Sempre em PR revisado.
- Em PR de fork: sem segredos, token só leitura, nada é enviado ao Code Scanning, `db-security` é pulado. `pull_request_target` não é usado.

### O que os testes de segurança cobrem
- **Autenticação**: e-mail inexistente e senha errada dão a mesma resposta e levam o mesmo tempo (BCrypt contra hash fictício); recuperação responde 202 sempre, token de uso único com expiração; BCrypt com custo ≥ 10; JWT `alg: none`, adulterado, de outro emissor, sem `exp` ou expirado → 401; o app não sobe sem `NIZE_JWT_SECRET` de 32+ bytes.
- **Limite de tentativas (REQ-047)**: 6ª falha de login no mesmo e-mail → 429 `TOO_MANY_ATTEMPTS`, igual para e-mail existente ou não; limites por IP no login, cadastro e recuperação.
- **Isolamento**: `IsolationCoverageTest` falha se um controller de dados não tiver teste que estenda `IsolationTestBase`; ArchUnit proíbe `userId` em DTO de entrada, exige `userId` nos métodos dos repositórios de dados e proíbe `findById` puro nos repositórios de categoria, cartão, pessoa e lançamento; o DAST confirma 404 entre usuárias.
- **Dados**: colunas de dinheiro `BIGINT`, senha e token só como hash, nenhuma coluna de CPF/cartão (`check-schema.sql`); usuário da aplicação sem `CREATE/ALTER/DROP/TRUNCATE/GRANT`; TLS obrigatório na URL do banco em `prod` (`DatabaseTlsGuard`); senhas, tokens e segredos fora de logs e respostas.
- **Sem papéis administrativos** no escopo atual: não há teste de escalonamento de privilégio.

## 4. Segredos e variáveis

Nunca grave valores em arquivo. `.env.example` lista só os nomes.

**GitHub → Settings → Secrets and variables → Actions**

| Nome | Tipo | Onde | Uso |
|---|---|---|---|
| `NIZE_DB_ROOT_PASSWORD_TEST` | secret | repositório | root do MySQL descartável dos testes (qualquer valor forte) |
| `NIZE_DB_MIGRATION_URL` | secret | Environment `production` | `jdbc:mysql://<host-aiven>:<porta>/nize?sslMode=REQUIRED` |
| `NIZE_DB_MIGRATION_USER` | secret | `production` | `nize_migrator` |
| `NIZE_DB_MIGRATION_PASSWORD` | secret | `production` | senha do usuário de migração |
| `RENDER_API_KEY` | secret | `production` | chave da API do Render (vale para a conta inteira: fica só aqui) |
| `RENDER_SERVICE_ID` | secret | `production` | `srv-...` do serviço `nize-api` |
| `CLOUDFLARE_API_TOKEN` | secret | `production` **e** `staging` | token só com "Cloudflare Pages: Edit" |
| `CLOUDFLARE_ACCOUNT_ID` | secret | `production` **e** `staging` | id da conta Cloudflare |
| `CF_PAGES_PROJECT` | variable | repositório | `nize` |
| `COVERAGE_MIN` | variable | repositório | `80` (opcional; padrão 80) |
| `COVERAGE_RULES_MIN` | variable | repositório | `80` (opcional; padrão 80) |
| `API_URL` | variable | `production` | `https://nize-api.onrender.com` (URL real do serviço) |

**Render → nize-api → Environment** (não ficam no GitHub): `NIZE_JWT_SECRET`, `NIZE_DB_URL`
(`jdbc:mysql://<host>:<porta>/nize?sslMode=REQUIRED`), `NIZE_DB_USER` (`nize_app`), `NIZE_DB_PASSWORD`.
`SPRING_PROFILES_ACTIVE=prod` e `PORT` já vêm da imagem / do Render.

**Cloudflare Pages → nize → Settings → Variables and Secrets**: `API_ORIGIN` = `https://nize-api.onrender.com`, em *Production* e *Preview*.

Diferenças em relação ao pedido original, e o motivo:
- **Sem `VITE_API_URL`**: o front continua chamando `/api` na própria origem, e uma Pages Function repassa para o Render (D-15). O efeito é o mesmo, sem CORS e com CSP `connect-src 'self'`.
- **Sem `NIZE_JWT_SECRET_TEST` nem senhas de usuários de teste como secrets**: o CI gera valores aleatórios em cada execução e mascara nos logs. Não há o que vazar nem rotacionar. Só o root do container descartável precisa de secret, porque o `services:` sobe antes dos steps.
- **API do Render em vez do deploy hook**: com a API, o pipeline espera o deploy ficar `live` antes do smoke test e registra o id do deploy para rollback.
- **trivy em vez de OWASP Dependency-Check**: é a mesma ferramenta da imagem, mais rápida e sem chave da NVD, e filtra por severidade.

## 5. Checklist de cadastro (uma vez)

Faça na ordem. **Crie os Environments antes do primeiro push dos workflows**: se o GitHub criar o `production` sozinho, ele nasce sem aprovação.

### GitHub
1. Settings → Environments → **New**: `staging` (sem revisores) e `production` (*Required reviewers*: você; *Deployment branches*: só `main`).
2. Settings → Actions → General: *Workflow permissions* = **Read repository contents**; desmarque "Allow GitHub Actions to create and approve pull requests"; em *Fork pull request workflows*, exija aprovação.
3. Settings → Advanced Security: ative **Dependabot alerts** e **Code scanning** (grátis em repositório público).
4. Cadastre os secrets e variables da tabela acima.

### Aiven (MySQL gratuito)
Limites atuais do plano free (conferido em 2026-11-01 em aiven.io/docs/products/mysql/concepts/mysql-free-tier): 1 CPU, 1 GB de RAM, 1 GB de disco, `max_connections` 76, sem IP fixo, sem VPC. Um serviço free por tipo e por conta. O serviço pode ser desligado se ficar sem uso; nesse caso, religue pelo console.
1. Crie a conta e o serviço **MySQL**, plano **Free**, na região mais próxima da do Render.
2. Em *Overview*, anote host e porta. O usuário `avnadmin` só serve para administração.
3. Conecte como `avnadmin` (o cliente pergunta a senha; não a coloque na linha de comando) e rode, com senhas fortes geradas na hora (`openssl rand -base64 24`):
   ```sql
   CREATE DATABASE nize CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE USER 'nize_migrator'@'%' IDENTIFIED BY '<senha 1>' REQUIRE SSL;
   GRANT CREATE, ALTER, DROP, INDEX, REFERENCES, SELECT, INSERT, UPDATE, DELETE ON nize.* TO 'nize_migrator'@'%';
   CREATE USER 'nize_app'@'%' IDENTIFIED BY '<senha 2>' REQUIRE SSL;
   GRANT SELECT, INSERT, UPDATE, DELETE ON nize.* TO 'nize_app'@'%';
   ```
   (Se o console da Aiven não aceitar `CREATE USER` por SQL, crie os usuários na aba *Users* e ajuste com `REVOKE ALL ... ; GRANT ...`.)
4. **TLS**: a Aiven exige TLS. Use `sslMode=REQUIRED`, que cifra a conexão mas não confere o certificado. `VERIFY_CA` exigiria o CA da Aiven (*Overview → CA certificate*) dentro de um truststore no container, mais as propriedades `trustCertificateKeyStoreUrl`/`Password` na URL. Fica como próximo passo.
5. **Rede**: nem o Render free nem os runners do GitHub têm IP fixo, então a lista de IPs permitidos fica aberta (`0.0.0.0/0`). A proteção vem de TLS obrigatório, senhas fortes e um usuário por função com privilégio mínimo. Alternativas, se um dia for preciso fechar por IP: runner próprio com IP fixo para a migração, e plano pago do Render com IP de saída estático.

### Cloudflare Pages
1. Crie o projeto: Workers & Pages → Create → Pages → *Direct Upload*, nome **`nize`**, branch de produção `main`. Pela CLI: `npx wrangler pages project create nize --production-branch=main`.
2. My Profile → API Tokens → *Create Custom Token*. Permissão: **Account → Cloudflare Pages → Edit**; recurso: só a sua conta; validade, se quiser.
3. O Account ID aparece na barra lateral do painel.
4. No projeto → Settings → Variables and Secrets: `API_ORIGIN` (Production e Preview).

### GHCR e Render
1. Rode o pipeline do back uma vez na `main`, ou com `target: backend`. O job *image* publica `ghcr.io/gabriellaferr00/nize-backend:<sha>`. O job *deploy* fica esperando aprovação: **não aprove ainda**.
2. GitHub → Packages → `nize-backend` → Package settings → **Change visibility → Public**. A imagem não contém segredos e o código já é público. Alternativa: deixar privada e cadastrar no Render uma credencial de registry com um token `read:packages`.
3. Render → New → **Web Service** → *Existing image* → `ghcr.io/gabriellaferr00/nize-backend:<sha>`. Nome **`nize-api`**, instância **Free**, *Health Check Path* `/actuator/health`, *Auto-Deploy* **desligado** (quem faz o deploy é o pipeline). Cadastre as variáveis de ambiente do Render da seção 4. Gere o `NIZE_JWT_SECRET` com `openssl rand -base64 48`.
4. Anote o `srv-...` (está na URL do serviço) em `RENDER_SERVICE_ID`. Em Account Settings → API Keys, crie a `RENDER_API_KEY`. Preencha `API_URL`.
5. Volte ao run do passo 1 e aprove o `production`.

## 6. Validação

Localmente, antes do push (com Docker):
```
docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:1.7.12          # workflows (+ shellcheck dos run:)
docker run --rm -i hadolint/hadolint < backend/Dockerfile                  # Dockerfile
docker run --rm -v "$PWD:/mnt" -w /mnt koalaman/shellcheck:stable -S warning scripts/ci/*.sh
python3 -I scripts/ci/check_pii.py && scripts/ci/check-forbidden-files.sh
cd backend && ./mvnw verify          # testes + cobertura
npm --prefix frontend run test:coverage && npm --prefix frontend run build
```

Sem merge na `main`:
1. `git switch -c ci/pipeline-test && git push -u origin ci/pipeline-test`.
2. Abra um Pull Request de `ci/pipeline-test` para `main`: o CI/CD roda sozinho. Depois que os workflows estiverem na `main`, também dá para usar Actions → **CI/CD** → *Run workflow* → branch, `target: both`.
3. Esperado: back verde até *Docker image + trivy* (sem push nem deploy); front publica o preview em `staging` e o smoke passa (precisa do `API_ORIGIN` de *Preview* apontando para uma API no ar).
4. Actions → **Security** → *Run workflow* (`scope: all`): roda também `db-security` e `security-tests`.
5. Depois do merge na `main`, aprove o Environment `production` (back e front).

## 7. Rollback

- **Render (back)**: nize-api → *Events*/*Deploys* → deploy anterior → **Rollback**. Isso volta para a imagem anterior. O resumo de cada run mostra a imagem e o id do deploy. Pela API: `POST /v1/services/{id}/rollback` com o `deployId`.
- **Cloudflare Pages (front)**: Workers & Pages → nize → *Deployments* → deployment anterior de produção → **Rollback to this deployment**. Outra opção: Actions → run anterior bem-sucedido na `main` → *Re-run all jobs*. Isso republica aquele commit, depois de passar de novo pelos testes e pela aprovação.
- **Migrações**: o pipeline nunca reverte dados. As migrações rodam **antes** do deploy, então a versão anterior do app precisa funcionar com o esquema novo. Por isso, siga *expand/contract*:
  1. **expand**: só acrescente (coluna nova anulável ou com padrão, tabela nova);
  2. publique o código que usa as duas formas;
  3. **contract**: remova o que ficou velho numa migração de uma versão seguinte.
  Para corrigir uma migração errada, crie a próxima (`V{n+1}__...`) em vez de editar a antiga. O Flyway valida o checksum. Se houver perda de dados, use os backups automáticos da Aiven pelo console.

## 8. Rotação de segredos

| Segredo | Como | Efeito |
|---|---|---|
| `NIZE_JWT_SECRET` | gere outro, troque no Render, *Manual Deploy → Restart* | **todas as sessões caem** (tokens antigos param de valer) e todo mundo entra de novo |
| Senha `nize_app` | `ALTER USER 'nize_app'@'%' IDENTIFIED BY '<nova>' RETAIN CURRENT PASSWORD;` → troque `NIZE_DB_PASSWORD` no Render e reinicie → `ALTER USER 'nize_app'@'%' DISCARD OLD PASSWORD;` | sem queda |
| Senha `nize_migrator` | `ALTER USER ...` e troque `NIZE_DB_MIGRATION_PASSWORD` no Environment | nenhum |
| `CLOUDFLARE_API_TOKEN` | crie um novo, troque nos dois Environments, revogue o antigo | nenhum |
| `RENDER_API_KEY` | crie uma nova, troque, revogue a antiga | nenhum |
| `NIZE_DB_ROOT_PASSWORD_TEST` | troque quando quiser | só afeta containers descartáveis |

Rotacione imediatamente se um valor aparecer em log, issue ou commit. O gitleaks e o trufflehog procuram em todo o histórico, mas apagar um commit não "desvaza" um segredo.

## 9. Cold start e limites do plano gratuito

- **Render free**: dorme após 15 min sem tráfego; a primeira requisição leva cerca de 1 min (Spring Boot + JVM). O smoke espera até 5 min, e o `HEALTHCHECK` da imagem tem 120 s de *start period*. São 750 h de instância por mês, o que basta para um serviço ligado o mês inteiro. A imagem pronta não gasta minutos de build. Memória: 512 MB. A JVM usa heap de 60% e GC serial; no teste local com 512 MB ficou em cerca de 220 MB.
- **Aiven free**: 1 GB de disco, 76 conexões (o app usa um pool de 5), pode ser desligada por inatividade e a Aiven pode trocar a região. Não é para produção de verdade.
- **Cloudflare Pages Functions**: 100 mil requisições por dia no plano gratuito. Só `/api/*` passa pela Function; o resto é estático.

## 10. Riscos residuais e próximos passos

- **Token em `localStorage` (D-12)**: um XSS leria o token. Mitigação: CSP sem script inline, `connect-src 'self'`, regras semgrep contra `dangerouslySetInnerHTML`/`innerHTML`, `npm audit`. Próximo passo possível: cookie `HttpOnly` (mudaria D-12).
- **Sem RLS** (MySQL): o isolamento depende da aplicação, coberto por `IsolationTestBase`, ArchUnit e DAST.
- **Logout só no cliente**: um token roubado vale até expirar (1 h). Não há lista de revogação.
- **Limite de tentativas em memória**: zera quando o serviço reinicia, inclusive depois de dormir no free tier. O IP pode ser forjado por quem chamar o Render direto. O limite por e-mail é o controle principal.
- **TLS sem verificação de CA** (`REQUIRED`) e **banco aberto à internet**, porque não há IP fixo (seção 5).
- **Recuperação de senha desligada em produção** até haver um provedor de e-mail (D-17; Open Question no design.md).
- **`RENDER_API_KEY` vale para a conta inteira**: guardada só no Environment com aprovação.
- **E2E ainda não confere o saldo do mês**: entra quando `GET /api/months/{m}` existir (TASK-034).
- **Free tiers sem SLA**: cold start, desligamento da Aiven por inatividade, mudança de região.
