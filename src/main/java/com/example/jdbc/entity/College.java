package com.example.jdbc.entity;

import com.example.jdbc.orm.annotation.Column;
import com.example.jdbc.orm.annotation.Id;
import com.example.jdbc.orm.annotation.Table;

import java.util.Objects;

@Table("college")
public class College {

    @Id(autoIncrement = true)
    @Column("id")
    private Integer id;

    @Column("name")
    private String name;

    @Column("code")
    private String code;

    public College() {
    }

    public College(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof College other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "College{id=" + id
                + ", name=" + quote(name)
                + ", code=" + quote(code)
                + '}';
    }

    private static String quote(String value) {
        return value == null ? "null" : "'" + value + "'";
    }
}
