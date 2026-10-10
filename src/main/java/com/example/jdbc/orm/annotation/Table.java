package com.example.jdbc.orm.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注实体类对应的数据库表名（ORM 映射中的「类 -> 表」）。
 *
 * <p>如果不标注，JDBCTool 会使用「类名转下划线小写」作为表名，
 * 例如 {@code Student} -> {@code student}。</p>
 *
 * @author homework
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Table {

    /** 数据库表名。 */
    String value() default "";
}
