package br.com.nize.repository;

import br.com.nize.domain.LoadedMonth;
import br.com.nize.domain.LoadedMonthId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LoadedMonthRepository extends JpaRepository<LoadedMonth, LoadedMonthId> {

    /** The months of the user that were already opened ({@code AAAA-MM}), for REQ-054. */
    @Query("select l.id.month from LoadedMonth l where l.id.userId = :userId")
    List<String> findMonthsByUserId(Long userId);
}
