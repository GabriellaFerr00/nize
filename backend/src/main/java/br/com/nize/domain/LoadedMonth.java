package br.com.nize.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/** Says "this month was already loaded", so opening it again never duplicates anything (D-7). */
@Entity
@Table(name = "loaded_month")
public class LoadedMonth {

    @EmbeddedId
    private LoadedMonthId id;

    @Column(name = "loaded_at", nullable = false)
    private Instant loadedAt;

    protected LoadedMonth() {
    }

    public LoadedMonth(Long userId, String month, Instant loadedAt) {
        this.id = new LoadedMonthId(userId, month);
        this.loadedAt = loadedAt;
    }

    public LoadedMonthId getId() { return id; }
}
