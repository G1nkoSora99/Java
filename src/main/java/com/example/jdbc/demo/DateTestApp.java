package com.example.jdbc.demo;

import com.example.jdbc.entity.College;
import com.example.jdbc.entity.Student;
import com.example.jdbc.orm.DataAccessException;
import com.example.jdbc.orm.JDBCTool;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 演示程序：使用 {@link JDBCTool} 完成数据库 DateTest 中
 * student（学生表）与 college（学院表）两张表的增删改查。
 *
 * <p>运行方式（任选其一）：</p>
 * <pre>
 * mvn -q compile exec:java
 * mvn -q package &amp;&amp; java -jar target/jdbc-orm-datetest-1.0.0.jar
 * </pre>
 *
 * @author homework
 */
public class DateTestApp {

    private static final String LINE = "=".repeat(88);
    private static final String THIN = "-".repeat(88);

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        banner();

        try (Connection connection = JDBCTool.getConnection()) {
            stepConnection(connection);
            stepOrmMapping();
            stepPrepareDatabase(connection);

            collegeCrud(connection);
            studentCrud(connection);
            customSqlDemo(connection);

            summary(connection, start);
        } catch (DataAccessException e) {
            printError(e.getMessage(), e.getCause());
        } catch (SQLException e) {
            // try-with-resources 自动关闭连接时可能抛出的受检异常
            printError("释放数据库连接失败", e);
        }
    }

    // ==================================================================================
    // 准备阶段
    // ==================================================================================

    private static void banner() {
        System.out.println(LINE);
        System.out.println("        ORM（对象关系映射）原理 + Java 反射机制 + JDBC 增删改查 综合演示");
        System.out.println("        数据库：DateTest        数据表：student（学生表） / college（学院表）");
        System.out.println("        核心工具类：com.example.jdbc.orm.JDBCTool");
        System.out.println(LINE);
    }

    private static void stepConnection(Connection connection) {
        printStep("[0-1] 建立数据库连接：JDBCTool.getConnection() 读取 db.properties");
        System.out.println("      连接成功 -> " + JDBCTool.describeConnection());
        System.out.println("      连接对象 -> " + connection.getClass().getName());
    }

    private static void stepOrmMapping() {
        printStep("[0-2] 反射解析出的 ORM 映射关系（类 <-> 表、属性 <-> 列）");
        System.out.println("      " + JDBCTool.describeMapping(College.class));
        System.out.println("      " + JDBCTool.describeMapping(Student.class));
        System.out.println();
        System.out.println("      由反射自动拼装的 SQL（无需为每张表手写 JDBC 代码）：");
        System.out.println("      ---- College ----");
        System.out.println(JDBCTool.describeSql(College.class));
        System.out.println("      ---- Student ----");
        System.out.println(JDBCTool.describeSql(Student.class));
    }

    /** 确保两张表存在，并清空历史数据，使演示可反复运行且输出一致。 */
    private static void stepPrepareDatabase(Connection connection) {
        printStep("[0-3] 准备数据表：确保 student / college 存在，并清空演示数据");
        JDBCTool.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `college` (
                  `id`   INT         NOT NULL AUTO_INCREMENT COMMENT '学院主键，自增',
                  `name` VARCHAR(64) NOT NULL COMMENT '学院名称',
                  `code` VARCHAR(32) NOT NULL COMMENT '学院代码',
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_college_code` (`code`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学院表'
                """, connection);
        JDBCTool.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `student` (
                  `id`              INT           NOT NULL AUTO_INCREMENT COMMENT '学生主键 Id，自增',
                  `name`            VARCHAR(64)   NOT NULL COMMENT '姓名',
                  `major`           VARCHAR(64)   NOT NULL COMMENT '专业',
                  `age`             INT                    DEFAULT NULL COMMENT '年龄',
                  `enrollment_date` DATE                   DEFAULT NULL COMMENT '入学时间',
                  `graduated`       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否毕业：1=已毕业，0=未毕业',
                  `tuition`         DECIMAL(10,2)          DEFAULT NULL COMMENT '学费',
                  PRIMARY KEY (`id`),
                  KEY `idx_student_major` (`major`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生表'
                """, connection);
        JDBCTool.executeUpdate("DELETE FROM `student`", connection);
        JDBCTool.executeUpdate("DELETE FROM `college`", connection);
        JDBCTool.executeUpdate("ALTER TABLE `student` AUTO_INCREMENT = 1", connection);
        JDBCTool.executeUpdate("ALTER TABLE `college` AUTO_INCREMENT = 1", connection);
        System.out.println("      表已就绪，当前 student=" + JDBCTool.count("student", connection)
                + " 行, college=" + JDBCTool.count("college", connection) + " 行");
    }

    // ==================================================================================
    // 演示一：学院表 college 的增删改查
    // ==================================================================================

    private static void collegeCrud(Connection connection) {
        printTitle("演示一：学院表 college 的增删改查");

        // ---------- 新增 ----------
        printStep("[1-1] save() —— 新增 3 个学院（主键自增，执行后由反射回填到对象属性）");
        List<College> colleges = List.of(
                new College("计算机学院", "CS"),
                new College("外国语学院", "FL"),
                new College("数学学院", "MATH"));
        for (College college : colleges) {
            System.out.println("      保存前 -> " + college);
            int rows = JDBCTool.save(college, connection);
            System.out.printf("      受影响行数 = %d ，保存后（已回填主键）-> %s%n", rows, college);
        }

        // ---------- 按主键查询 ----------
        printStep("[1-2] getOneById() —— 按主键查询单个学院");
        College found = JDBCTool.getOneById("2", College.class, connection);
        System.out.println("      getOneById(\"2\")  -> " + found);
        System.out.println("      getOneById(\"99\") -> " + JDBCTool.getOneById("99", College.class, connection)
                + "   （不存在时返回 null）");

        // ---------- 更新 ----------
        printStep("[1-3] update() —— 修改学院名称与代码（只改属性，SQL 自动生成）");
        if (found != null) {
            System.out.println("      修改前 -> " + found);
            found.setName("外国语学院(国际学院)");
            found.setCode("FL-INTL");
            int rows = JDBCTool.update(found, connection);
            System.out.println("      受影响行数 = " + rows);
            System.out.println("      修改后 -> " + JDBCTool.getOneById("2", College.class, connection));
        }

        // ---------- 结果集转对象列表 ----------
        printStep("[1-4] resultSetToList() —— 把 ResultSet 映射成 College 对象列表");
        String sql = "SELECT id, name, code FROM college ORDER BY id";
        System.out.println("      SQL: " + sql);
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<College> list = JDBCTool.resultSetToList(rs, College.class);
            ConsoleTable.print(List.of("id", "学院名称", "学院代码"), collegeRows(list));
            System.out.println("      映射后的对象列表（每个元素都是一个 College 对象）：");
            for (College c : list) {
                System.out.println("        " + c);
            }
        } catch (SQLException e) {
            throw new DataAccessException("查询学院列表失败", e);
        }

        // ---------- 删除 ----------
        printStep("[1-5] delete() —— 按主键删除学院");
        College toDelete = new College();
        toDelete.setId(3);
        System.out.println("      待删除对象（只需主键有值）-> " + toDelete);
        int rows = JDBCTool.delete(toDelete, connection);
        System.out.println("      受影响行数 = " + rows);
        System.out.println("      删除后 college 表剩余 " + JDBCTool.count("college", connection) + " 行");
    }

    // ==================================================================================
    // 演示二：学生表 student 的增删改查
    // ==================================================================================

    private static void studentCrud(Connection connection) {
        printTitle("演示二：学生表 student 的增删改查（含 入学时间 / 是否毕业 / 学费 等类型）");

        // ---------- 新增 ----------
        printStep("[2-1] save() —— 新增 4 名学生");
        List<Student> students = List.of(
                new Student("张伟", "计算机科学与技术", 19, LocalDate.of(2021, 9, 1), false, new BigDecimal("5200.00")),
                new Student("李娜", "软件工程", 20, LocalDate.of(2021, 9, 1), true, new BigDecimal("5800.00")),
                new Student("王强", "人工智能", 18, LocalDate.of(2024, 9, 1), false, new BigDecimal("6800.00")),
                new Student("陈静", "网络工程", 21, LocalDate.of(2020, 9, 1), true, new BigDecimal("5000.00")));
        for (Student student : students) {
            System.out.println("      保存前 -> " + student);
            int rows = JDBCTool.save(student, connection);
            System.out.printf("      受影响行数 = %d ，保存后（已回填 id）-> %s%n", rows, student);
        }

        // ---------- 按主键查询 ----------
        printStep("[2-2] getOneById() —— 按主键查询单个学生");
        Student found = JDBCTool.getOneById("3", Student.class, connection);
        System.out.println("      getOneById(\"3\") -> " + found);
        System.out.println("      注意：入学时间字段在数据库中是 DATE，映射到 Java 后是 LocalDate："
                + (found == null ? "null" : found.getEnrollmentDate().getClass().getName()));

        // ---------- 更新 ----------
        printStep("[2-3] update() —— 修改学生学费与毕业状态");
        if (found != null) {
            System.out.println("      修改前 -> " + found);
            found.setTuition(new BigDecimal("7200.00"));
            found.setGraduated(true);
            found.setAge(21);
            int rows = JDBCTool.update(found, connection);
            System.out.println("      受影响行数 = " + rows);
            System.out.println("      修改后 -> " + JDBCTool.getOneById("3", Student.class, connection));
        }

        // ---------- 结果集转对象列表 ----------
        printStep("[2-4] resultSetToList() —— 查询全部学生并映射成 Student 对象列表");
        String sql = "SELECT id, name, major, age, enrollment_date, graduated, tuition "
                + "FROM student ORDER BY id";
        System.out.println("      SQL: " + sql);
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Student> list = JDBCTool.resultSetToList(rs, Student.class);
            ConsoleTable.print(List.of("id", "姓名", "专业", "年龄", "入学时间", "是否毕业", "学费"),
                    studentRows(list));
        } catch (SQLException e) {
            throw new DataAccessException("查询学生列表失败", e);
        }

        // ---------- 删除 ----------
        printStep("[2-5] delete() —— 按主键删除学生");
        Student toDelete = new Student();
        toDelete.setId(4);
        System.out.println("      待删除对象（只需主键有值）-> " + toDelete);
        int rows = JDBCTool.delete(toDelete, connection);
        System.out.println("      受影响行数 = " + rows);

        List<Student> remaining = JDBCTool.query(
                "SELECT * FROM student ORDER BY id", Student.class, connection);
        ConsoleTable.print(List.of("id", "姓名", "专业", "年龄", "入学时间", "是否毕业", "学费"),
                studentRows(remaining));
    }

    // ==================================================================================
    // 演示三：自定义 SQL + 通用查询
    // ==================================================================================

    private static void customSqlDemo(Connection connection) {
        printTitle("演示三：resultSetToList() / query() 处理自定义 SQL（含部分列与聚合列）");

        printStep("[3-1] 只查询部分列：结果集中没有的列，对应属性保持 null（宽松映射）");
        String sql1 = "SELECT id, name, major, tuition FROM student WHERE graduated = 1 ORDER BY tuition DESC";
        System.out.println("      SQL: " + sql1);
        List<Student> graduatedStudents = JDBCTool.query(sql1, Student.class, connection);
        ConsoleTable.print(List.of("id", "姓名", "专业", "年龄", "入学时间", "是否毕业", "学费"),
                studentRows(graduatedStudents));
        System.out.println("      注意：本次 SQL 没有查询 age / enrollment_date / graduated 三列，");
        System.out.println("            所以映射出的对象里这三个属性是 null（NULL），学费列正常映射。");
        for (Student s : graduatedStudents) {
            System.out.println("        " + s);
        }

        printStep("[3-2] 带占位符参数的条件查询：query(sql, clazz, connection, params...)");
        String sql2 = "SELECT * FROM student WHERE tuition >= ? AND age >= ? ORDER BY tuition DESC";
        System.out.println("      SQL: " + sql2 + "   params = [6000, 18]");
        List<Student> rich = JDBCTool.query(sql2, Student.class, connection, new BigDecimal("6000"), 18);
        ConsoleTable.print(List.of("id", "姓名", "专业", "年龄", "入学时间", "是否毕业", "学费"),
                studentRows(rich));

        printStep("[3-3] 聚合查询：结果集中的 student_count / avg_tuition 在 Student 中没有对应属性，被自动忽略");
        String sql3 = "SELECT major, COUNT(*) AS student_count, AVG(tuition) AS avg_tuition "
                + "FROM student GROUP BY major ORDER BY major";
        System.out.println("      SQL: " + sql3);
        List<Student> grouped = JDBCTool.query(sql3, Student.class, connection);
        System.out.println("      映射结果（仅 major 列被成功映射，其余属性保持 null）：");
        for (Student s : grouped) {
            System.out.println("        " + s);
        }
    }

    // ==================================================================================
    // 结束
    // ==================================================================================

    private static void summary(Connection connection, long startTime) {
        printTitle("演示结束：数据已真实写入数据库 DateTest");
        System.out.println("  最终 college 表记录数 = " + JDBCTool.count("college", connection));
        System.out.println("  最终 student 表记录数 = " + JDBCTool.count("student", connection));
        System.out.println("  可在 MySQL 客户端中复核： USE DateTest; SELECT * FROM student; SELECT * FROM college;");
        System.out.println("  总耗时 = " + (System.currentTimeMillis() - startTime) + " ms");
        System.out.println(LINE);
    }

    // ==================================================================================
    // 输出辅助
    // ==================================================================================

    private static void printTitle(String title) {
        System.out.println();
        System.out.println(LINE);
        System.out.println("  " + title);
        System.out.println(LINE);
    }

    /** 统一打印异常信息，并给出常见排查方向。 */
    private static void printError(String message, Throwable cause) {
        System.out.println();
        System.out.println("[错误] " + message);
        if (cause != null) {
            System.out.println("       原因：" + cause.getMessage());
        }
        System.out.println("       请确认：1) MySQL 服务已启动；2) 已执行 sql/date_test.sql 创建数据库 DateTest；");
        System.out.println("               3) src/main/resources/db.properties 中的账号密码正确。");
    }

    private static void printStep(String title) {
        System.out.println();
        System.out.println(THIN);
        System.out.println("  " + title);
        System.out.println(THIN);
    }

    private static List<List<String>> collegeRows(List<College> colleges) {
        List<List<String>> rows = new ArrayList<>();
        for (College c : colleges) {
            rows.add(List.of(
                    text(c.getId()),
                    text(c.getName()),
                    text(c.getCode())));
        }
        return rows;
    }

    private static List<List<String>> studentRows(List<Student> students) {
        List<List<String>> rows = new ArrayList<>();
        for (Student s : students) {
            rows.add(List.of(
                    text(s.getId()),
                    text(s.getName()),
                    text(s.getMajor()),
                    text(s.getAge()),
                    text(s.getEnrollmentDate()),
                    s.getGraduatedText(),
                    s.getTuitionText()));
        }
        return rows;
    }

    /** 表格单元格文本：null 统一显示为 NULL，避免与字符串 "null" 混淆。 */
    private static String text(Object value) {
        return value == null ? "NULL" : value.toString();
    }
}
