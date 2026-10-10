package com.example.jdbc.entity;

import com.example.jdbc.orm.annotation.Column;
import com.example.jdbc.orm.annotation.Id;
import com.example.jdbc.orm.annotation.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 学生实体（对应数据库 DateTest 中的 student 表）。
 *
 * <p>表结构：Id、name、major、age、入学时间、是否毕业、学费。</p>
 *
 * <p>本类是标准的 JavaBean：无参构造方法 + 私有属性 + getter/setter，
 * 这样才能被 JDBCTool 通过反射完成「创建对象 + 属性赋值」。</p>
 *
 * @author homework
 */
@Table("student")
public class Student {

    /** 主键 Id，数据库自增。 */
    @Id(autoIncrement = true)
    @Column("id")
    private Integer id;

    /** 姓名。 */
    @Column("name")
    private String name;

    /** 专业。 */
    @Column("major")
    private String major;

    /** 年龄。 */
    @Column("age")
    private Integer age;

    /** 入学时间。属性名 enrollmentDate 按下划线规则对应列 enrollment_date。 */
    @Column("enrollment_date")
    private LocalDate enrollmentDate;

    /** 是否毕业：true 已毕业，false 未毕业。对应 TINYINT(1) 列 graduated。 */
    @Column("graduated")
    private Boolean graduated;

    /** 学费。使用 BigDecimal 保证金额精度，对应 DECIMAL(10,2) 列 tuition。 */
    @Column("tuition")
    private BigDecimal tuition;

    /** 无参构造方法：反射创建对象时必需。 */
    public Student() {
    }

    /** 便捷构造方法（不含主键，主键由数据库生成）。 */
    public Student(String name, String major, Integer age, LocalDate enrollmentDate,
                   Boolean graduated, BigDecimal tuition) {
        this.name = name;
        this.major = major;
        this.age = age;
        this.enrollmentDate = enrollmentDate;
        this.graduated = graduated;
        this.tuition = tuition;
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

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public LocalDate getEnrollmentDate() {
        return enrollmentDate;
    }

    public void setEnrollmentDate(LocalDate enrollmentDate) {
        this.enrollmentDate = enrollmentDate;
    }

    public Boolean getGraduated() {
        return graduated;
    }

    public void setGraduated(Boolean graduated) {
        this.graduated = graduated;
    }

    public BigDecimal getTuition() {
        return tuition;
    }

    public void setTuition(BigDecimal tuition) {
        this.tuition = tuition;
    }

    /** @return 「是否毕业」的中文描述，便于控制台演示。 */
    public String getGraduatedText() {
        if (graduated == null) {
            return "NULL";
        }
        return graduated ? "已毕业" : "未毕业";
    }

    /** @return 学费的展示文本。 */
    public String getTuitionText() {
        return tuition == null ? "NULL" : String.format("%,.2f", tuition);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Student other)) {
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
        return "Student{id=" + id
                + ", 姓名=" + quote(name)
                + ", 专业=" + quote(major)
                + ", 年龄=" + age
                + ", 入学时间=" + enrollmentDate
                + ", 是否毕业=" + getGraduatedText()
                + ", 学费=" + getTuitionText()
                + '}';
    }

    private static String quote(String value) {
        return value == null ? "null" : "'" + value + "'";
    }
}
