package com.wince.fincore.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interest_run")
public class InterestRun {
    protected InterestRun() {}
    public InterestRun(UUID id, int year, int month) {
        this.id = id; this.runYear = year; this.runMonth = month; this.executedAt = Instant.now();
    }
    @Id @Column(name="id",nullable=false) private UUID id;
    @Column(name="run_year",nullable=false) private int runYear;
    @Column(name="run_month",nullable=false) private int runMonth;
    @Column(name="executed_at",nullable=false) private Instant executedAt;
    public UUID getId(){return id;}
    public int getRunYear(){return runYear;}
    public int getRunMonth(){return runMonth;}
    public Instant getExecutedAt(){return executedAt;}
}
