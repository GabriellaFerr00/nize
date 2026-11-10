# Card brand icons (REQ-052, D-21)

Two sources, both saved here (nothing is fetched from the internet when the page opens):

## 1. Simple Icons (one-color icons)

Icons from [Simple Icons](https://simpleicons.org) **16.36.0** (npm `simple-icons`), released under
[CC0 1.0](https://github.com/simple-icons/simple-icons/blob/develop/LICENSE.md). The only change is the `fill`
attribute, so each icon reads on its brand-colored tile (white or near-black, ≥ 4:1).

All names and logos are trademarks of their owners. Nize only uses them so the user can recognise her own cards;
it does not claim any affiliation. Brands without a freely licensed icon are shown as initials on the brand color,
and the user can always send her own image instead (`PUT /api/cards/{id}/logo`).

Do not add logos taken from bank websites or other sources without a license that allows it.

| File | Brand | Simple Icons slug | Source listed by Simple Icons |
|---|---|---|---|
| `nubank.svg` | Nubank | `nubank` | https://nubank.com.br/en/press/ |
| ~~`mercadopago.svg`~~ (replaced, see 2) | Mercado Pago | `mercadopago` | https://www.mercadopago.com |
| ~~`picpay.svg`~~ (replaced, see 2) | PicPay | `picpay` | https://www.picpay.com/site/sobre-nos |
| `pagbank.svg` | PagSeguro | `pagseguro` | https://pagseguro.uol.com.br |
| `carrefour.svg` | Carrefour | `carrefour` | https://en.wikipedia.org/wiki/File:Carrefour_logo_no_tag.svg |
| `amex.svg` | American Express | `americanexpress` | https://commons.wikimedia.org/wiki/File:American_Express_logo_(2018).svg |
| `diners.svg` | Diners Club | `dinersclub` | https://www.dinersclub.com/about-us/press |
| `visa.svg` | Visa | `visa` | https://merchantsignageeu.visa.com/product.asp?dptID=696 |
| `mastercard.svg` | MasterCard | `mastercard` | https://www.mastercard.com/brandcenter/us/en/download-artwork.html |

## 2. Open Finance Brasil (full-color logos)

Logos the institutions themselves publish in the public directory of Open Finance Brasil, taken from the npm package
[`logos-bancos-br`](https://github.com/rzmt/logos-bancos-br) **0.8.6** (files named by ISPB, unchanged). The package
code is MIT, but **the logos are trademarks of the institutions and are not covered by that license**: they are
redistributed for nominative use (to identify the institution in an interface), which is how Nize uses them: the
user recognises her own card. Shown on a white tile. If an institution asks, the file is removed.
Each SVG was checked before saving: no script, event handler, foreignObject or external link.

| File | Institution | ISPB | Source published by the institution |
|---|---|---|---|
| `bb.svg` | Banco do Brasil | 00000000 | https://www.bb.com.br/docs/pub/inst/img/LogoBB.svg |
| `itau.svg` | Itaú | 60701190 | https://www.itau.com.br/media/dam/m/4ee2c952e1fa91a2/original/Novo_itau.svg |
| `bradesco.svg` | Bradesco | 60746948 | https://banco.bradesco/open-finance/logo/icones_vetorial-pf.svg |
| `santander.svg` | Santander | 90400888 | https://cms.santander.com.br/sites/WPS/imagem/santander_chama/23-10-31_194829_P_santander_chama.svg |
| `caixa.svg` | Caixa | 00360305 | https://consentimento.openbanking.caixa.gov.br/assets/images/logomarca_caixa.svg |
| `inter.svg` | Inter | 00416968 | https://marketing.bancointer.com.br/arquivos/images/app-icon.svg |
| `c6.svg` | C6 Bank | 31872495 | https://cdn.c6bank.com.br/open-banking/c6bank-logo.svg |
| `picpay.svg` | PicPay | 22896431 | https://picpay.s3.sa-east-1.amazonaws.com/openbanking/picpay-logo-icon-pf.svg |
| `mercadopago.svg` | Mercado Pago | 10573521 | https://http2.mlstatic.com/open-banking/assets/logo.svg |
