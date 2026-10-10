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

public class DateTestApp {

    private static final List<String> COLLEGE_COLUMNS = List.of("id", "name", "code");

    private static final List<String> STUDENT_COLUMNS = List.of(
            "id", "name", "major", "age", "enrollment_date", "graduated", "tuition");

    public static void main(String[] args) {
        try (Connection connection = JDBCTool.getConnection()) {
            initSchema(connection);
            college(connection);
            student(connection);
            customSql(connection);
        } catch (SQLException e) {
            throw new DataAccessException(e.getMessage(), e);
        }
    }

    private static void initSchema(Connection connection) {
        JDBCTool.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `college` (
                  `id`   INT         NOT NULL AUTO_INCREMENT,
                  `name` VARCHAR(64) NOT NULL,
                  `code` VARCHAR(32) NOT NULL,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_college_code` (`code`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """, connection);
        JDBCTool.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `student` (
                  `id`              INT           NOT NULL AUTO_INCREMENT,
                  `name`            VARCHAR(64)   NOT NULL,
                  `major`           VARCHAR(64)   NOT NULL,
                  `age`             INT                    DEFAULT NULL,
                  `enrollment_date` DATE                   DEFAULT NULL,
                  `graduated`       TINYINT(1)    NOT NULL DEFAULT 0,
                  `tuition`         DECIMAL(10,2)          DEFAULT NULL,
                  PRIMARY KEY (`id`),
                  KEY `idx_student_major` (`major`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """, connection);
        JDBCTool.executeUpdate("DELETE FROM `student`", connection);
        JDBCTool.executeUpdate("DELETE FROM `college`", connection);
        JDBCTool.executeUpdate("ALTER TABLE `student` AUTO_INCREMENT = 1", connection);
        JDBCTool.executeUpdate("ALTER TABLE `college` AUTO_INCREMENT = 1", connection);
    }

    private static void college(Connection connection) {
        List<College> colleges = List.of(
                new College("计算机学院", "CS"),
                new College("外国语学院", "FL"),
                new College("数学学院", "MATH"));
        for (College college : colleges) {
            System.out.println(JDBCTool.save(college, connection) + " " + college);
        }

        System.out.println(JDBCTool.getOneById("2", College.class, connection));
        System.out.println(JDBCTool.getOneById("99", College.class, connection));

        College college = JDBCTool.getOneById("2", College.class, connection);
        college.setName("外国语学院(国际学院)");
        college.setCode("FL-INTL");
        System.out.println(JDBCTool.update(college, connection) + " " + college);
        System.out.println(JDBCTool.getOneById("2", College.class, connection));

        String sql = "SELECT id, name, code FROM college ORDER BY id";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            ConsoleTable.print(COLLEGE_COLUMNS, collegeRows(JDBCTool.resultSetToList(rs, College.class)));
        } catch (SQLException e) {
            throw new DataAccessException(e.getMessage(), e);
        }

        College toDelete = new College();
        toDelete.setId(3);
        System.out.println(JDBCTool.delete(toDelete, connection));
        ConsoleTable.print(COLLEGE_COLUMNS, collegeRows(
                JDBCTool.query("SELECT * FROM college ORDER BY id", College.class, connection)));
    }

    private static void student(Connection connection) {
        List<Student> students = List.of(
                new Student("张伟", "计算机科学与技术", 19, LocalDate.of(2021, 9, 1), false, new BigDecimal("5200.00")),
                new Student("李娜", "软件工程", 20, LocalDate.of(2021, 9, 1), true, new BigDecimal("5800.00")),
                new Student("王强", "人工智能", 18, LocalDate.of(2024, 9, 1), false, new BigDecimal("6800.00")),
                new Student("陈静", "网络工程", 21, LocalDate.of(2020, 9, 1), true, new BigDecimal("5000.00")));
        for (Student student : students) {
            System.out.println(JDBCTool.save(student, connection) + " " + student);
        }

        System.out.println(JDBCTool.getOneById("3", Student.class, connection));

        Student student = JDBCTool.getOneById("3", Student.class, connection);
        student.setAge(21);
        student.setGraduated(true);
        student.setTuition(new BigDecimal("7200.00"));
        System.out.println(JDBCTool.update(student, connection) + " " + student);
        System.out.println(JDBCTool.getOneById("3", Student.class, connection));

        String sql = "SELECT id, name, major, age, enrollment_date, graduated, tuition "
                + "FROM student ORDER BY id";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            ConsoleTable.print(STUDENT_COLUMNS, studentRows(JDBCTool.resultSetToList(rs, Student.class)));
        } catch (SQLException e) {
            throw new DataAccessException(e.getMessage(), e);
        }

        Student toDelete = new Student();
        toDelete.setId(4);
        System.out.println(JDBCTool.delete(toDelete, connection));
        ConsoleTable.print(STUDENT_COLUMNS, studentRows(
                JDBCTool.query("SELECT * FROM student ORDER BY id", Student.class, connection)));
    }

    private static void customSql(Connection connection) {
        ConsoleTable.print(STUDENT_COLUMNS, studentRows(JDBCTool.query(
                "SELECT id, name, major, tuition FROM student WHERE graduated = 1 ORDER BY tuition DESC",
                Student.class, connection)));

        ConsoleTable.print(STUDENT_COLUMNS, studentRows(JDBCTool.query(
                "SELECT * FROM student WHERE tuition >= ? AND age >= ? ORDER BY tuition DESC",
                Student.class, connection, new BigDecimal("6000"), 18)));
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
                    text(s.getGraduated()),
                    text(s.getTuition())));
        }
        return rows;
    }

    private static String text(Object value) {
        return value == null ? "NULL" : value.toString();
    }
}
