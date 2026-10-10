package com.example.jdbc.entity;

import com.example.jdbc.orm.annotation.Column;
import com.example.jdbc.orm.annotation.Id;
import com.example.jdbc.orm.annotation.Table;

import java.util.Objects;

/**
 * 学院实体（对应数据库 DateTest 中的 college 表）。
 *
 * <p>表结构：id、name、code。</p>
 *
 * @author homework
 */
@Table("college")
public class College {

    /** 主键 id，数据库自增。 */
    @Id(autoIncrement = true)
    @Column("id")
    private Integer id;

    /** 学院名称。 */
    @Column("name")
    private String name;

    /** 学院代码。 */
    @Column("code")
    private String code;

    /** 无参构造方法：反射创建对象时必需。 */
    public College() {
    }

    /** 便捷构造方法（不含主键，主键由数据库生成）。 */
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
        return "College{id=" + id + ", name='" + name + '\'' + ", code='" + code + '\'' + '}';
    }
}
