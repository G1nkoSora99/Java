package com.example.jdbc.orm.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注实体的主键属性。
 *
 * <p>被标注的字段用于生成 {@code WHERE 主键 = ?} 条件（update / delete / getOneById），
 * 并且当 {@link #autoIncrement()} 为 {@code true} 时，save 会自动回填数据库生成的主键值。</p>
 *
 * @author homework
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Id {

    /** 该主键是否为数据库自增列。自增列在 INSERT 语句中会被自动排除。 */
    boolean autoIncrement() default true;
}
