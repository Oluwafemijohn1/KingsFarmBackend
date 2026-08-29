package com.kingsfarm.kingsfarmbackend.wholeegg;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Page<Customer> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select c from Customer c where lower(c.firstName) like lower(concat('%', :q, '%')) " +
            "or lower(c.lastName) like lower(concat('%', :q, '%')) or c.phone like concat('%', :q, '%')")
    List<Customer> search(@Param("q") String query, Pageable pageable);
}
