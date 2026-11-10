package br.com.nize;

import java.math.BigDecimal;

/** Reads a DECIMAL(12,2) money column (reais) as cents, so tests also check what is stored (D-2). */
public final class DbMoney {

    private DbMoney() {
    }

    public static long cents(Object column) {
        return ((BigDecimal) column).movePointRight(2).longValueExact();
    }
}
