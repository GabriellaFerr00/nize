package br.com.nize.repository;

import br.com.nize.domain.LoadedMonth;
import br.com.nize.domain.LoadedMonthId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoadedMonthRepository extends JpaRepository<LoadedMonth, LoadedMonthId> {
}
