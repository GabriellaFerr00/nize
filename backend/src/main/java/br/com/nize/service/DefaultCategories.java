package br.com.nize.service;

import java.util.List;

/**
 * The 15 factory categories (REQ-010). Names and descriptions are user-facing Portuguese data and
 * mirror the table "Categorias de fábrica" in design.md; keep both in sync.
 */
final class DefaultCategories {

    record Entry(String name, String description) {
    }

    static final List<Entry> ALL = List.of(
            new Entry("Mercado", "Despensa e geladeira cheias: feira, supermercado e itens de limpeza do lar."),
            new Entry("Necessidades", "Itens do dia a dia que não podem faltar: farmácia, higiene pessoal e pequenas coisas da casa."),
            new Entry("Eletrônicos", "Aparelhos e acessórios tecnológicos, além de consertos e manutenção deles."),
            new Entry("Assinaturas", "Serviços cobrados todo mês ou todo ano, como streaming, música e aplicativos."),
            new Entry("Roupa", "Roupas, calçados e acessórios para vestir, em loja física ou online."),
            new Entry("Beleza", "Cuidados com a aparência: salão, unhas, maquiagem e produtos para pele e cabelo."),
            new Entry("Presentes", "Mimos e lembranças para quem você gosta, em aniversários e datas especiais."),
            new Entry("Saúde", "Cuidados com o corpo e a mente: academia, suplementos, consultas e exames."),
            new Entry("Despesas eventuais", "Imprevistos e gastos que não se repetem todo mês: conserto do carro, veterinário, consulta fora do plano."),
            new Entry("Desenvolvimento", "Investimento em aprender e crescer: cursos, livros, agendas e materiais de estudo."),
            new Entry("Uber/transporte", "Deslocamentos: aplicativos de corrida, transporte público e combustível."),
            new Entry("IFood/restaurante", "Refeições fora de casa: restaurantes, lanchonetes e pedidos por aplicativo."),
            new Entry("Lazer", "Diversão e encontros: festas, cinema, teatro, passeios e comemorações."),
            new Entry("Aluguel", "Valor mensal da moradia: aluguel ou prestação do imóvel."),
            new Entry("Contas", "Contas da casa que chegam todo mês: internet, água, luz, condomínio e IPTU."));

    private DefaultCategories() {
    }
}
