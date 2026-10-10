package com.example.jdbc.orm.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注实体属性对应的数据库列名（ORM 映射中的「属性 -> 字段」）。
 *
 * <p>如果不标注，JDBCTool 会使用「属性名转下划线小写」作为列名，
 * 例如 {@code enrollmentDate} -> {@code enrollment_date}。</p>
 *
 * @author homework
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {

    /** 数据库列名。 */
    String value() default "";
}
