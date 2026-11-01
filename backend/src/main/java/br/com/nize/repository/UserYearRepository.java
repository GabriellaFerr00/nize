package br.com.nize.repository;

import br.com.nize.domain.UserYear;
import br.com.nize.domain.UserYearId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserYearRepository extends JpaRepository<UserYear, UserYearId> {
}
