package com.snackcounter.model;

/** Someone who signed up to keep a snack counter stocked. {@code homeOffice} is where they are on payroll. */
public record Volunteer(String employeeId, String name, Office homeOffice) {}
