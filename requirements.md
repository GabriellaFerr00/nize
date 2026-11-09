# requirements.md
> Nize — Version 0.1.9 (MVP web) — 2026-11-09

Fonte: `Nize — Funcionalidades e escopo do MVP web.pdf` (1 nov 2026). Em caso de dúvida entre este arquivo e o PDF, pergunte antes de implementar.

## Overview
O Nize é a versão web da planilha "Finanças 2026". A usuária lança gastos e entradas mês a mês e vê saldo, gastos por categoria e quanto gastou em cada intervalo entre os dias de fechamento. Não conecta a bancos nem executa Pix: "Pix" e os nomes de cartão (Nubank, Bradesco etc.) são apenas rótulos de organização. O MVP cobre tudo o que a aba do mês da planilha faz, mais o panorama anual.

## Actors
- **Usuária**: pessoa autenticada que controla suas próprias finanças. Só ela enxerga seus dados.
- **Pessoa (terceiro)**: não é um ator do sistema; é um cadastro de quem usa o cartão da usuária (ex.: "Mainha"). Não faz login.

## Glossário
- **Fixo**: gasto recorrente (tipo `fixo`), com marca "pago".
- **Cartão**: gasto de crédito (tipo `cartao`), com cartão e parcelas.
- **Posição**: soma dos gastos com data efetiva dentro de um intervalo entre fechamentos (as entradas não entram).
- **Saldo próprio**: saldo do mês + gastos de terceiros − reembolsos de terceiros.
- **Total de crédito**: soma dos gastos com forma de pagamento crédito (cartão ou fixo no crédito).
- Valores em dinheiro: inteiros em centavos. Mês: texto `AAAA-MM`.

## Functional Requirements

### Conta e acesso
- **REQ-001**: A usuária shall poder criar conta com nome, e-mail e senha.
  - _Acceptance_: cadastro válido cria a conta e permite login; e-mail repetido é rejeitado com erro claro.
- **REQ-002**: A usuária shall poder entrar e sair com e-mail e senha.
  - _Acceptance_: credenciais corretas dão acesso; incorretas retornam 401 sem revelar qual campo errou.
- **REQ-003**: A usuária shall poder recuperar a senha.
  - _Acceptance_: pedido de recuperação gera um link/token de uso único com validade; após uso, a nova senha funciona e o token deixa de valer. (Ver Open Question sobre envio de e-mail.)
  - Em produção o token nunca é escrito no log (NFR-008); enquanto não houver provedor de e-mail, a recuperação fica indisponível em produção e o pedido continua respondendo 202 sem revelar se o e-mail existe.
- **REQ-004**: O sistema shall isolar os dados por usuária.
  - _Acceptance_: teste automatizado em que a usuária A não lê, altera nem exclui nenhuma linha da usuária B em nenhum endpoint (resposta 404).
- **REQ-047**: O sistema shall limitar tentativas em login, cadastro, pedido e uso de recuperação de senha, bloqueando temporariamente quem ultrapassar o limite.
  - _Acceptance_: a 6ª falha de login para o mesmo e-mail em 15 min recebe 429 `TOO_MANY_ATTEMPTS` (com `Retry-After`) mesmo com a senha certa, até o fim da janela; a resposta é a mesma para e-mail cadastrado ou não. Limites: login 5 falhas por e-mail e 20 por IP em 15 min; cadastro 10 por IP por hora; pedido de recuperação 3 por e-mail e 10 por IP por hora; uso de token de recuperação 10 falhas por IP por hora. Login certo zera o contador do e-mail. (Requisito novo, pedido pela usuária em 2026-11-01.)
- **REQ-005**: O sistema shall exibir um onboarding curto na primeira entrada da usuária.
  - _Acceptance_: aparece no primeiro login, pode ser dispensado e não reaparece depois.

### Configuração e anos
- **REQ-006**: A usuária shall poder definir o ano vigente, que é o ano exibido por padrão.
  - _Acceptance_: ao trocar o ano vigente, a tela do mês abre nesse ano no próximo acesso.
- **REQ-007**: A usuária shall poder criar um novo ano, que copia categorias, cartões e pessoas do ano anterior.
  - _Acceptance_: após criar 2027, as listas de 2027 são cópias das de 2026 e editá-las não altera 2026.
- **REQ-008**: A usuária shall poder consultar e alterar dados de anos anteriores.
  - _Acceptance_: ao abrir 2026 estando em 2027 vigente, todos os lançamentos continuam editáveis.
- **REQ-009**: A usuária shall poder ligar ou desligar a permissão de parcelas automáticas.
  - _Acceptance_: com a permissão desligada, o formulário de lançamento não oferece geração automática.
- **REQ-048**: A usuária shall poder ligar ou desligar os fechamentos do mês e escolher quantos dias de fechamento quiser (1 a 31); conta nova começa ligada, com os dias 15 e 31.
  - _Acceptance_: conta nova mostra posições nos dias 15 e 31 (último dia); com os dias 5, 10 e 20 a tela mostra três posições; desligado, a tela do mês não mostra posições e os demais totais não mudam.

### Categorias, cartões e pessoas
- **REQ-010**: O sistema shall entregar as categorias de fábrica, cada uma com descrição, para cada ano.
  - _Acceptance_: uma conta nova tem as 15 categorias de fábrica no ano vigente (lista em `design.md`, "Categorias de fábrica"). "Mainha" não é categoria: terceiros são cadastrados como pessoas.
- **REQ-011**: A usuária shall poder criar, renomear e desativar categorias.
  - _Acceptance_: categoria desativada não aparece no formulário de lançamento, mas lançamentos antigos continuam exibindo-a.
- **REQ-012**: A usuária shall poder excluir categorias, respeitando a regra de lançamentos existentes.
  - _Acceptance_: categoria sem lançamentos é excluída na hora; categoria com lançamentos no mês não é excluída, e o sistema pergunta se a usuária quer alterar esses lançamentos antes (a categoria continua existindo).
- **REQ-013**: A usuária shall poder criar, renomear e desativar cartões, cada um com dia de vencimento (1–31).
  - _Acceptance_: dia de vencimento fora de 1–31 é rejeitado.
- **REQ-014**: A usuária shall poder criar, renomear e desativar pessoas (terceiros que usam seu cartão).
  - _Acceptance_: pessoa desativada não aparece no formulário, mas lançamentos antigos continuam exibindo-a.

### Lançamentos de gastos
- **REQ-015**: A usuária shall poder lançar gasto fixo com nome, categoria, valor, forma de pagamento (Pix ou crédito, este com cartão opcional; ex.: assinatura paga no cartão), data e marca "pago".
  - _Acceptance_: o fixo aparece na lista de Fixos do mês e entra no total de fixos e no total de gastos. Um fixo no crédito também entra no total de crédito e no resumo por cartão.
- **REQ-016**: A usuária shall poder lançar gasto de cartão de crédito com nome, categoria, valor, cartão, data e parcelas.
  - _Acceptance_: o gasto aparece na lista de Cartão do mês e entra no total de gastos.
- **REQ-017**: A usuária shall poder marcar um gasto de terceiro informando a pessoa e o cartão usado.
  - _Acceptance_: o gasto fica no total do mês e aparece no resumo por pessoa.
- **REQ-018**: Com a permissão de parcelas automáticas ligada, a usuária shall poder lançar a compra uma vez, informando número de parcelas e valor total ou valor da parcela, e o sistema shall criar uma parcela por mês a partir do mês escolhido.
  - _Acceptance_: cenários "Parcelas automáticas" do PDF passam (R$ 100,00 em 3x → 3333, 3333, 3334; 4x de R$ 50,00 → 4×5000; sem parcelas → 1 lançamento; permissão desligada → 1 lançamento).
- **REQ-019**: A usuária shall poder lançar parcelas mês a mês, digitando cada parcela manualmente.
  - _Acceptance_: lançamento manual com `parcela_atual`/`parcelas_total` cria apenas aquela parcela.
- **REQ-020**: O sistema shall dividir valor total em parcelas sem perder centavos, deixando a diferença na última parcela.
  - _Acceptance_: R$ 100,00 em 3 parcelas = 3333, 3333 e 3334 centavos; a soma das parcelas é sempre o valor total.
- **REQ-021**: O sistema shall exibir o indicador de parcela no formato "N/M" no mês.
  - _Acceptance_: lançamento com parcela 7 de 12 aparece como "7/12".
- **REQ-022**: Ao abrir um mês pela primeira vez, o sistema shall copiar os fixos do mês anterior com "pago" desmarcado e criar as parcelas (de gastos e de entradas parceladas) que caem nele.
  - _Acceptance_: a próxima parcela também é criada para parcelas lançadas à mão, mas só com a permissão de parcelas automáticas ligada (desligada: só os fixos são copiados); cenários "Carga ao abrir um mês" do PDF passam; gastos avulsos não são copiados; parcela 12/12 não gera nova parcela; parcelas já geradas não são recriadas; abrir o mesmo mês duas vezes não duplica nada; a virada dez→jan segue a mesma regra e abre o novo ano.
- **REQ-023**: A usuária shall poder editar e excluir qualquer dado (lançamentos, entradas, categorias, cartões, pessoas), inclusive o que foi carregado automaticamente.
  - _Acceptance_: editar um fixo carregado no mês não altera o mês anterior.

### Entradas
- **REQ-024**: A usuária shall poder lançar entradas do mês (salários e outras) com nome, data e valor.
  - _Acceptance_: a entrada soma no total de entradas do mês.
- **REQ-025**: A usuária shall poder lançar entradas parceladas (ex.: 1/3).
  - _Acceptance_: a carga automática cria as próximas parcelas da entrada nos meses seguintes.
- **REQ-026**: A usuária shall poder registrar reembolso de terceiro como entrada ligada a uma pessoa.
  - _Acceptance_: entrada com `pessoa_id` é tratada como reembolso e entra no cálculo do saldo próprio.

### Totais e posição do mês
- **REQ-027**: O sistema shall calcular o total de gastos do mês como a soma de fixos e cartão.
  - _Acceptance_: fixos 99399 + cartão 494889 = 594288 centavos.
- **REQ-028**: O sistema shall calcular o total de fixos do mês.
  - _Acceptance_: o total de fixos é a soma apenas dos lançamentos de tipo `fixo`.
- **REQ-029**: O sistema shall calcular o saldo do mês como entradas menos total de gastos.
  - _Acceptance_: entradas 662358 − gastos 594288 = 68070 centavos.
- **REQ-030**: Com os fechamentos ligados (REQ-048), o sistema shall calcular a posição de cada intervalo entre fechamentos, sem acumular os intervalos anteriores: o primeiro vai do dia 1 até o primeiro fechamento, cada um dos seguintes vai do dia seguinte ao fechamento anterior até o seu, e o último vai até o fim do mês. Cada posição é só a soma dos gastos com data efetiva (REQ-032) dentro do seu intervalo; as entradas não entram e não há "sobra" nem "falta".
  - _Acceptance_: entradas 300000 e gastos 120000 do dia 1 ao 15 → 120000; gastos 30000 do dia 16 ao 30 → 30000 (não 150000); intervalo sem gastos → 0; com fechamentos 10 e 20, os intervalos são 1–10 e 11–fim do mês.
- **REQ-031**: Um dia de fechamento além do fim do mês shall valer como o último dia do mês (o padrão 31 é sempre o último dia; 30 e 31 em fevereiro viram 28/29).
  - _Acceptance_: com fechamentos 15 e 31 em abril, gastos 180000 no dia 30 entram no intervalo 16–30; em fevereiro, o limite é o dia 28/29.
- **REQ-032**: O sistema shall datar gastos no Pix pela data do pagamento e gastos no crédito pelo dia de vencimento do cartão, e cada parcela shall pesar no mês em que aparece.
  - _Acceptance_: Pix de 20000 pago em 12/04 pesa até o dia 15; compra de 50000 no crédito, cartão com vencimento dia 10, lançada em abril, pesa até o dia 15 de abril e não na data da compra.
- **REQ-033**: A marca "pago" shall ser apenas controle e não alterar totais nem posições.
  - _Acceptance_: marcar fixo de 43500 como pago deixa todos os totais e posições iguais.
- **REQ-034**: O sistema shall calcular o saldo próprio como saldo do mês + gastos de terceiros − reembolsos.
  - _Acceptance_: saldo 50000, gastos de terceiros 20000 e reembolsos 19000 → saldo próprio 51000.

### Resumos
- **REQ-035**: O sistema shall exibir o resumo de gastos por categoria, com valor e porcentagem sobre o total de gastos.
  - _Acceptance_: "Despesas eventuais" 141814 de 594288 = 23,86%; a soma por categoria é igual ao total de gastos.
- **REQ-036**: O sistema shall exibir o resumo de gastos por forma de pagamento.
  - _Acceptance_: a soma por forma de pagamento é igual ao total de gastos.
- **REQ-037**: O sistema shall exibir o resumo de gastos por cartão, incluindo o grupo "Sem cartão".
  - _Acceptance_: crédito de 47500 sem cartão aparece em "Sem cartão"; a soma por cartão (com "Sem cartão") é igual ao total de crédito.
- **REQ-038**: O sistema shall exibir o resumo de gastos de terceiros por pessoa e por cartão.
  - _Acceptance_: a soma por pessoa é igual ao total de gastos de terceiros do mês.

### Panorama anual e navegação
- **REQ-039**: O sistema shall exibir o panorama anual com gráfico de gastos e entradas dos 12 meses e tabela com saldo mensal.
  - _Acceptance_: valores de cada mês coincidem com os totais da tela do mês; clicar em um mês abre esse mês.
- **REQ-040**: A usuária shall poder navegar entre meses e anos.
  - _Acceptance_: trocar de mês/ano atualiza cabeçalho, listas e resumos.

### Telas
- **REQ-041**: A tela do mês shall concentrar cabeçalho (mês, total de gastos, total de fixos, saldo, posições nos dias de fechamento), listas de fixos, cartão e entradas, e painel de resumos com saldo próprio.
  - _Acceptance_: todos os itens acima visíveis na mesma tela.
- **REQ-042**: A usuária shall poder lançar gasto ou entrada numa janela sobre o mês, com "salvar e lançar outro em seguida".
  - _Acceptance_: um gasto é lançado em poucos segundos sem sair da tela do mês.
- **REQ-043**: O sistema shall oferecer menu lateral no computador e barra inferior no celular.
  - _Acceptance_: em largura de 375px o menu é a barra inferior e não há rolagem horizontal da página.
- **REQ-044**: O sistema shall ter telas de Categorias, Cartões, Pessoas e Configurações.
  - _Acceptance_: cada tela permite as ações listadas nos REQ-009 a REQ-014.
- **REQ-045**: O Nize shall ter identidade visual, textos e layout próprios, sem copiar o material da planilha.
  - _Acceptance_: revisão visual manual antes do primeiro deploy; nenhum texto ou imagem da planilha reaproveitado.
  - Identidade "Verde Nize" (D-20): logotipo próprio, modo claro e escuro e contraste WCAG 2.2 AA. Um seletor com Claro e Escuro troca o tema na hora e a escolha é lembrada no próximo acesso; antes de qualquer escolha, o primeiro acesso abre no tema do aparelho. Valores e regras não mudam com o tema. (Seletor pedido pela usuária em 2026-11-05.)

### Idioma
- **REQ-046**: A usuária shall poder escolher o idioma da interface entre inglês e português.
  - _Acceptance_: um seletor de idioma troca todos os textos das telas sem recarregar dados; a escolha é lembrada no próximo acesso; valores, datas e regras de cálculo não mudam com o idioma. O idioma padrão é o português. (Requisito novo, pedido pela usuária em 2026-11-01; padrão confirmado em 2026-11-01.)

## Non-Functional Requirements
- **NFR-001**: Todo valor em dinheiro é inteiro em centavos, nunca decimal.
  - _Measurement_: nenhum `double`/`float`/`BigDecimal` em valores monetários no código; colunas `BIGINT`; revisão de código e teste de arquitetura.
- **NFR-002**: Saldo, totais e porcentagens não são persistidos; são calculados dos lançamentos.
  - _Measurement_: nenhuma coluna de total/saldo no schema.
- **NFR-003**: As regras de cálculo têm testes escritos antes do código; todos os cenários do PDF existem como testes automatizados.
  - _Measurement_: cada cenário do PDF mapeado a um teste; `mvn test` verde.
- **NFR-004**: Todo acesso a dados é filtrado por `usuario_id`.
  - _Measurement_: suíte de teste de isolamento (REQ-004) cobre todos os endpoints de dados.
- **NFR-005**: Senhas armazenadas apenas com hash adaptativo (BCrypt ou Argon2).
  - _Measurement_: inspeção do schema e teste de que a senha em texto puro nunca é persistida nem logada.
- **NFR-006**: A tela do mês funciona em telas de 360px a desktop.
  - _Measurement_: verificação manual em 360px, 768px e 1280px.
- **NFR-007** (meta confirmada pela usuária em 2026-11-04): `GET` do resumo do mês responde em < 500 ms no p95 com 500 lançamentos no mês.
  - _Measurement_: teste de carga simples local. Meta proposta pela spec, não vem do PDF; confirmar.

- **NFR-008**: Senhas, tokens (JWT e de recuperação) e o segredo `NIZE_JWT_SECRET` nunca aparecem em logs nem em respostas da API.
  - _Measurement_: testes que capturam o log no login, cadastro e recuperação; grep no log da aplicação nos testes de integração do CI.
- **NFR-009**: Em produção, a aplicação acessa o banco com um usuário que só tem `SELECT, INSERT, UPDATE, DELETE`, por conexão com TLS obrigatório; as migrações rodam num passo separado, com outro usuário.
  - _Measurement_: teste no CI que tenta `CREATE/ALTER/DROP/TRUNCATE` com o usuário da aplicação e espera falha; a aplicação não sobe no perfil `prod` sem `sslMode=REQUIRED` (ou `VERIFY_CA`/`VERIFY_IDENTITY`) na URL.
- **NFR-010**: Nenhum deploy acontece sem testes unitários, de integração (H2 e MySQL), cobertura mínima de 80% (back e front, configurável) e portão de segurança aprovados.
  - _Measurement_: pipeline do GitHub Actions (`docs/ci-cd.md`); críticos/altos bloqueiam o deploy.

## Out of Scope (0.1.0)
- Open Finance, Pix real, importação automática de extrato.
- Investimentos e metas (fase 2): entidades `aporte_investimento`, `meta`, `aporte_meta`.
- Busca e filtros nos lançamentos; importar e exportar CSV (fase 2).
- App mobile e uso offline (fase 3).
- Compartilhar dados com outras pessoas.
- Migração de dados da planilha existente (os valores do PDF são só exemplos de teste).
- Revenda do Nize ou cópia de textos/layout da planilha.

## Changelog
| Version | Date | Change |
|---|---|---|
| 0.1.0 | 2026-11-01 | Initial spec, derivada do PDF do MVP web |
| 0.1.1 | 2026-11-01 | REQ-046 (idioma da interface en/pt) |
| 0.1.2 | 2026-11-01 | REQ-015: fixo pode ser pago no crédito; glossário "Total de crédito" |
| 0.1.3 | 2026-11-01 | REQ-022: carga continua parcelas à mão e respeita a permissão |
| 0.1.9 | 2026-11-09 | REQ-030/031: cada posição é só a soma dos gastos do intervalo (sem entradas, sem sobra/falta); decisão da usuária |
| 0.1.8 | 2026-11-05 | REQ-045: identidade Verde Nize, modo claro/escuro com seletor de tema (pedido da usuária) |
| 0.1.7 | 2026-11-04 | REQ-030: posição por intervalo entre fechamentos, sem acumular; o último intervalo vai até o fim do mês. Diverge do PDF (posição acumulada até 15/30); decisão da usuária |
| 0.1.6 | 2026-11-04 | NFR-007 confirmada (p95 < 500 ms com 500 lançamentos) |
| 0.1.5 | 2026-11-04 | REQ-048 (fechamentos opcionais, quantos dias quiser, padrão 15 e 31); REQ-030/031/041 passam a usar os dias de fechamento. Diverge do PDF (dias fixos 15 e 30); decisão da usuária |
| 0.1.4 | 2026-11-01 | REQ-047 (limite de tentativas), NFR-008 a NFR-010 (segredos fora do log, banco com privilégio mínimo e TLS, pipeline de CI/CD); REQ-003 em produção sem e-mail |
