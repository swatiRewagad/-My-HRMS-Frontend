package com.hrms.cms.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "holidays", indexes = {
    @Index(name = "idx_holiday_date", columnList = "holiday_date", unique = true),
    @Index(name = "idx_holiday_year", columnList = "\"year\"")
})
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate holidayDate;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 50)
    private String type;

    @Column(name = "\"year\"", nullable = false)
    private Integer year;

    @Column(name = "is_national")
    private boolean national = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getHolidayDate() { return holidayDate; }
    public void setHolidayDate(LocalDate holidayDate) { this.holidayDate = holidayDate; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public boolean isNational() { return national; }
    public void setNational(boolean national) { this.national = national; }
}
