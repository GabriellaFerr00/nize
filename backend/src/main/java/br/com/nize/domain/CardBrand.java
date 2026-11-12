package br.com.nize.domain;

import java.util.List;

/**
 * Card brands used in Brazil (REQ-052, D-21): the keys a card may store in {@code brand}. Names, colors and icons
 * live in the front (frontend/src/lib/cardBrands.ts); {@code CardBrandSyncTest} keeps both key lists equal.
 */
public final class CardBrand {

    public static final List<String> KEYS = List.of(
            "nubank",
            "itau",
            "bradesco",
            "santander",
            "bb",
            "caixa",
            "inter",
            "c6",
            "mercadopago",
            "picpay",
            "pagbank",
            "neon",
            "btg",
            "xp",
            "sicredi",
            "sicoob",
            "porto",
            "carrefour",
            "amazon",
            "amex",
            "diners",
            "visa",
            "mastercard",
            "elo",
            "hipercard");

    /** For {@code @Pattern}: one of the keys. */
    public static final String PATTERN = "nubank|itau|bradesco|santander|bb|caixa|inter|c6|mercadopago|picpay|pagbank|neon|btg|xp|sicredi|sicoob|porto|carrefour|amazon|amex|diners|visa|mastercard|elo|hipercard";

    private CardBrand() {
    }
}
