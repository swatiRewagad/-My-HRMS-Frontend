package com.hrms.cms.repository;

import com.hrms.cms.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findByYear(int year);

    @Query("SELECT h FROM Holiday h WHERE h.holidayDate BETWEEN :start AND :end")
    List<Holiday> findBetweenDates(@Param("start") LocalDate start, @Param("end") LocalDate end);

    boolean existsByHolidayDate(LocalDate date);
}
